;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.services-files-test
  (:require
   [app.common.uuid :as uuid]
   [app.db :as db]
   [app.db.sql :as sql]
   [app.http :as http]
   [app.storage :as sto]
   [app.test-helpers :as th]
   [app.util.time :as dt]
   [clojure.test :as t]
   [datoteka.core :as fs]))

(t/use-fixtures :once th/state-init)
(t/use-fixtures :each th/database-reset)

(t/deftest files-crud
  (let [prof    (th/create-profile* 1 {:is-active true})
        team-id (:default-team-id prof)
        proj-id (:default-project-id prof)
        file-id (uuid/next)
        page-id (uuid/next)]

    (t/testing "create file"
      (let [data {::th/type :create-file
                  :profile-id (:id prof)
                  :project-id proj-id
                  :id file-id
                  :name "foobar"
                  :is-shared false}
            out (th/mutation! data)]

        ;; (th/print-result! out)
        (t/is (nil? (:error out)))

        (let [result (:result out)]
          (t/is (= (:name data) (:name result)))
          (t/is (= proj-id (:project-id result))))))

    (t/testing "rename file"
      (let [data {::th/type :rename-file
                  :id file-id
                  :name "new name"
                  :profile-id (:id prof)}
            out  (th/mutation! data)]

        ;; (th/print-result! out)
        (let [result (:result out)]
          (t/is (= (:id data) (:id result)))
          (t/is (= (:name data) (:name result))))))

    (t/testing "query files"
      (let [data {::th/type :project-files
                  :project-id proj-id
                  :profile-id (:id prof)}
            out  (th/query! data)]

        ;; (th/print-result! out)
        (t/is (nil? (:error out)))

        (let [result (:result out)]
          (t/is (= 1 (count result)))
          (t/is (= file-id (get-in result [0 :id])))
          (t/is (= "new name" (get-in result [0 :name]))))))

    (t/testing "query single file without users"
      (let [data {::th/type :file
                  :profile-id (:id prof)
                  :id file-id}
            out  (th/query! data)]

        ;; (th/print-result! out)
        (t/is (nil? (:error out)))

        (let [result (:result out)]
          (t/is (= file-id (:id result)))
          (t/is (= "new name" (:name result)))
          (t/is (= 1 (count (get-in result [:data :pages]))))
          (t/is (nil? (:users result))))))

    (t/testing "delete file"
      (let [data {::th/type :delete-file
                  :id file-id
                  :profile-id (:id prof)}
            out (th/mutation! data)]
        ;; (th/print-result! out)
        (t/is (nil? (:error out)))
        (t/is (nil? (:result out)))))

    (t/testing "query single file after delete"
      (let [data {::th/type :file
                  :profile-id (:id prof)
                  :id file-id}
            out (th/query! data)]

        ;; (th/print-result! out)

        (let [error      (:error out)
              error-data (ex-data error)]
          (t/is (th/ex-info? error))
          (t/is (= (:type error-data) :not-found)))))

    (t/testing "query list files after delete"
      (let [data {::th/type :project-files
                  :project-id proj-id
                  :profile-id (:id prof)}
            out  (th/query! data)]

        ;; (th/print-result! out)
        (t/is (nil? (:error out)))

        (let [result (:result out)]
          (t/is (= 0 (count result))))))
    ))

(t/deftest file-gc-task
  (letfn [(create-file-media-object [{:keys [profile-id file-id]}]
            (let [mfile  {:filename "sample.jpg"
                          :path (th/tempfile "app/test_files/sample.jpg")
                          :mtype "image/jpeg"
                          :size 312043}
                  params {::th/type :upload-file-media-object
                          :profile-id profile-id
                          :file-id file-id
                          :is-local true
                          :name "testfile"
                          :content mfile}
                  out    (th/mutation! params)]

              ;; (th/print-result! out)

              (t/is (nil? (:error out)))
              (:result out)))

          (update-file [{:keys [profile-id file-id changes revn] :or {revn 0}}]
            (let [params {::th/type :update-file
                          :id file-id
                          :session-id (uuid/random)
                          :profile-id profile-id
                          :revn revn
                          :changes changes}
                  out    (th/mutation! params)]
              (t/is (nil? (:error out)))
              (:result out)))]

    (let [storage (:app.storage/storage th/*system*)

          profile (th/create-profile* 1)
          file    (th/create-file* 1 {:profile-id (:id profile)
                                      :project-id (:default-project-id profile)
                                      :is-shared false})

          fmo1    (create-file-media-object {:profile-id (:id profile)
                                             :file-id (:id file)})
          fmo2    (create-file-media-object {:profile-id (:id profile)
                                             :file-id (:id file)})
          shid    (uuid/random)

          ures    (update-file
                   {:file-id (:id file)
                    :profile-id (:id profile)
                    :revn 0
                    :changes
                    [{:type :add-obj
                      :page-id (first (get-in file [:data :pages]))
                      :id shid
                      :parent-id uuid/zero
                      :frame-id uuid/zero
                      :obj {:id shid
                            :name "image"
                            :frame-id uuid/zero
                            :parent-id uuid/zero
                            :type :image
                            :metadata {:id (:id fmo1)}}}]})]

      ;; Check that reference storage objets on filemediaobjects
      ;; are the same because of deduplication feature.
      (t/is (= (:media-id fmo1) (:media-id fmo2)))
      (t/is (= (:thumbnail-id fmo1) (:thumbnail-id fmo2)))

      ;; If we launch gc-touched-task, we should have 2 items to
      ;; freeze because of the deduplication (we have uploaded 2 times
      ;; 2 two same files).
      (let [task (:app.storage/gc-touched-task th/*system*)
            res  (task {})]

        (t/is (= 2 (:freeze res)))
        (t/is (= 0 (:delete res))))

      ;; run the task immediately
      (let [task  (:app.tasks.file-gc/handler th/*system*)
            res   (task {})]
        (t/is (= 0 (:processed res))))

      ;; make the file eligible for GC waiting 300ms (configured
      ;; timeout for testing)
      (th/sleep 300)

      ;; run the task again
      (let [task  (:app.tasks.file-gc/handler th/*system*)
            res   (task {})]
        (t/is (= 1 (:processed res))))

      ;; retrieve file and check trimmed attribute
      (let [row (db/exec-one! th/*pool* ["select * from file where id = ?" (:id file)])]
        (t/is (true? (:has-media-trimmed row))))

      ;; check file media objects
      (let [rows (db/exec! th/*pool* ["select * from file_media_object where file_id = ?" (:id file)])]
        (t/is (= 1 (count rows))))

      ;; The underlying storage objects are still available.
      (t/is (some? @(sto/get-object storage (:media-id fmo2))))
      (t/is (some? @(sto/get-object storage (:thumbnail-id fmo2))))
      (t/is (some? @(sto/get-object storage (:media-id fmo1))))
      (t/is (some? @(sto/get-object storage (:thumbnail-id fmo1))))

      ;; now, we have deleted the unused file-media-object, if we
      ;; execute the touched-gc task, we should see that two of them
      ;; are marked to be deleted.
      (let [task (:app.storage/gc-touched-task th/*system*)
            res  (task {})]
        (t/is (= 2 (:freeze res)))
        (t/is (= 0 (:delete res))))

      ;; Finally, check that some of the objects that are marked as
      ;; deleted we are unable to retrieve them using standard storage
      ;; public api.
      (t/is (some? @(sto/get-object storage (:media-id fmo2))))
      (t/is (some? @(sto/get-object storage (:thumbnail-id fmo2))))
      (t/is (some? @(sto/get-object storage (:media-id fmo1))))
      (t/is (some? @(sto/get-object storage (:thumbnail-id fmo1))))

      )))

(t/deftest permissions-checks-creating-file
  (let [profile1 (th/create-profile* 1)
        profile2 (th/create-profile* 2)

        data     {::th/type :create-file
                  :profile-id (:id profile2)
                  :project-id (:default-project-id profile1)
                  :name "foobar"
                  :is-shared false}
        out      (th/mutation! data)
        error    (:error out)]

    ;; (th/print-result! out)
    (t/is (th/ex-info? error))
    (t/is (th/ex-of-type? error :not-found))))

(t/deftest permissions-checks-rename-file
  (let [profile1 (th/create-profile* 1)
        profile2 (th/create-profile* 2)

        file     (th/create-file* 1 {:project-id (:default-project-id profile1)
                                     :profile-id (:id profile1)})
        data     {::th/type :rename-file
                  :id (:id file)
                  :profile-id (:id profile2)
                  :name "foobar"}
        out      (th/mutation! data)
        error    (:error out)]

    ;; (th/print-result! out)
    (t/is (th/ex-info? error))
    (t/is (th/ex-of-type? error :not-found))))

(t/deftest permissions-checks-delete-file
  (let [profile1 (th/create-profile* 1)
        profile2 (th/create-profile* 2)

        file     (th/create-file* 1 {:project-id (:default-project-id profile1)
                                     :profile-id (:id profile1)})
        data     {::th/type :delete-file
                  :profile-id (:id profile2)
                  :id (:id file)}
        out      (th/mutation! data)
        error    (:error out)]

    ;; (th/print-result! out)
    (t/is (th/ex-info? error))
    (t/is (th/ex-of-type? error :not-found))))

(t/deftest permissions-checks-set-file-shared
  (let [profile1 (th/create-profile* 1)
        profile2 (th/create-profile* 2)
        file     (th/create-file* 1 {:project-id (:default-project-id profile1)
                                     :profile-id (:id profile1)})
        data     {::th/type :set-file-shared
                  :profile-id (:id profile2)
                  :id (:id file)
                  :is-shared true}
        out      (th/mutation! data)
        error    (:error out)]

    ;; (th/print-result! out)
    (t/is (th/ex-info? error))
    (t/is (th/ex-of-type? error :not-found))))

(t/deftest permissions-checks-link-to-library-1
  (let [profile1 (th/create-profile* 1)
        profile2 (th/create-profile* 2)
        file1    (th/create-file* 1 {:project-id (:default-project-id profile1)
                                     :profile-id (:id profile1)
                                     :is-shared true})
        file2    (th/create-file* 2 {:project-id (:default-project-id profile1)
                                     :profile-id (:id profile1)})

        data     {::th/type :link-file-to-library
                  :profile-id (:id profile2)
                  :file-id (:id file2)
                  :library-id (:id file1)}

        out      (th/mutation! data)
        error    (:error out)]

      ;; (th/print-result! out)
      (t/is (th/ex-info? error))
      (t/is (th/ex-of-type? error :not-found))))

(t/deftest permissions-checks-link-to-library-2
  (let [profile1 (th/create-profile* 1)
        profile2 (th/create-profile* 2)
        file1    (th/create-file* 1 {:project-id (:default-project-id profile1)
                                     :profile-id (:id profile1)
                                     :is-shared true})

        file2    (th/create-file* 2 {:project-id (:default-project-id profile2)
                                     :profile-id (:id profile2)})

        data     {::th/type :link-file-to-library
                  :profile-id (:id profile2)
                  :file-id (:id file2)
                  :library-id (:id file1)}

        out      (th/mutation! data)
        error    (:error out)]

      ;; (th/print-result! out)
      (t/is (th/ex-info? error))
      (t/is (th/ex-of-type? error :not-found))))

(t/deftest deletion
  (let [task     (:app.tasks.objects-gc/handler th/*system*)
        profile1 (th/create-profile* 1)
        file     (th/create-file* 1 {:project-id (:default-project-id profile1)
                                     :profile-id (:id profile1)})]
    ;; file is not deleted because it does not meet all
    ;; conditions to be deleted.
    (let [result (task {:max-age (dt/duration 0)})]
      (t/is (nil? result)))

    ;; query the list of files
    (let [data {::th/type :project-files
                :project-id (:default-project-id profile1)
                :profile-id (:id profile1)}
          out  (th/query! data)]
      ;; (th/print-result! out)
      (t/is (nil? (:error out)))
      (let [result (:result out)]
        (t/is (= 1 (count result)))))

    ;; Request file to be deleted
    (let [params {::th/type :delete-file
                  :id (:id file)
                  :profile-id (:id profile1)}
          out    (th/mutation! params)]
      (t/is (nil? (:error out))))

    ;; query the list of files after soft deletion
    (let [data {::th/type :project-files
                :project-id (:default-project-id profile1)
                :profile-id (:id profile1)}
          out  (th/query! data)]
      ;; (th/print-result! out)
      (t/is (nil? (:error out)))
      (let [result (:result out)]
        (t/is (= 0 (count result)))))

    ;; run permanent deletion (should be noop)
    (let [result (task {:max-age (dt/duration {:minutes 1})})]
      (t/is (nil? result)))

    ;; query the list of file libraries of a after hard deletion
    (let [data {::th/type :file-libraries
                :file-id (:id file)
                :profile-id (:id profile1)}
          out  (th/query! data)]
      ;; (th/print-result! out)
      (t/is (nil? (:error out)))
      (let [result (:result out)]
        (t/is (= 0 (count result)))))

    ;; run permanent deletion
    (let [result (task {:max-age (dt/duration 0)})]
      (t/is (nil? result)))

    ;; query the list of file libraries of a after hard deletion
    (let [data {::th/type :file-libraries
                :file-id (:id file)
                :profile-id (:id profile1)}
          out  (th/query! data)]
      ;; (th/print-result! out)
      (let [error (:error out)
            error-data (ex-data error)]
        (t/is (th/ex-info? error))
        (t/is (= (:type error-data) :not-found))))
    ))

(t/deftest query-frame-thumbnails
  (let [prof (th/create-profile* 1 {:is-active true})
        file (th/create-file* 1 {:profile-id (:id prof)
                                 :project-id (:default-project-id prof)
                                 :is-shared false})
        data {::th/type :file-frame-thumbnails
              :profile-id (:id prof)
              :file-id (:id file)
              :frame-id (uuid/next)}]

    ;; insert an entry on the database with a test value for the thumbnail of this frame
    (th/db-insert! :file-frame-thumbnail
                   {:file-id (:file-id data)
                    :frame-id (:frame-id data)
                    :data "testvalue"})

    (let [{:keys [result error] :as out} (th/query! data)]
      ;; (th/print-result! out)
      (t/is (nil? error))
      (t/is (= 1 (count result)))
      (t/is (= "testvalue" (get result (:frame-id data)))))))

(t/deftest insert-frame-thumbnails
  (let [prof (th/create-profile* 1 {:is-active true})
        file (th/create-file* 1 {:profile-id (:id prof)
                                 :project-id (:default-project-id prof)
                                 :is-shared false})
        data {::th/type :upsert-file-frame-thumbnail
              :profile-id (:id prof)
              :file-id (:id file)
              :frame-id (uuid/next)
              :data "test insert new value"}]

    (let [out (th/mutation! data)]
      (t/is (nil? (:error out)))
      (t/is (nil? (:result out)))
      (let [[result] (th/db-query :file-frame-thumbnail
                                  {:file-id (:file-id data)
                                   :frame-id (:frame-id data)})]
        (t/is (= "test insert new value" (:data result)))))))

(t/deftest upsert-frame-thumbnails
  (let [prof (th/create-profile* 1 {:is-active true})
        file (th/create-file* 1 {:profile-id (:id prof)
                                 :project-id (:default-project-id prof)
                                 :is-shared false})
        data {::th/type :upsert-file-frame-thumbnail
              :profile-id (:id prof)
              :file-id (:id file)
              :frame-id (uuid/next)
              :data "updated value"}]

    ;; insert an entry on the database with and old value for the thumbnail of this frame
    (th/db-insert! :file-frame-thumbnail
                   {:file-id (:file-id data)
                    :frame-id (:frame-id data)
                    :data "old value"})

    (let [out (th/mutation! data)]
      ;; (th/print-result! out)

      (t/is (nil? (:error out)))
      (t/is (nil? (:result out)))

      ;; retrieve the value from the database and check its content
      (let [[result] (th/db-query :file-frame-thumbnail
                                  {:file-id (:file-id data)
                                   :frame-id (:frame-id data)})]
        (t/is (= "updated value" (:data result)))))))


(t/deftest file-thumbnail-ops
  (let [prof (th/create-profile* 1 {:is-active true})
        file (th/create-file* 1 {:profile-id (:id prof)
                                 :project-id (:default-project-id prof)
                                 :revn 2
                                 :is-shared false})
        data {::th/type :file-thumbnail
              :profile-id (:id prof)
              :file-id (:id file)}]

    (t/testing "query a thumbnail with single revn"

      ;; insert an entry on the database with a test value for the thumbnail of this frame
      (th/db-insert! :file-thumbnail
                     {:file-id (:file-id data)
                      :revn 1
                      :data "testvalue1"})

      (let [{:keys [result error] :as out} (th/query! data)]
        ;; (th/print-result! out)
        (t/is (nil? error))
        (t/is (= 4 (count result)))
        (t/is (= "testvalue1" (:data result)))
        (t/is (= 1 (:revn result)))))

    (t/testing "query thumbnail with two revisions"
      ;; insert an entry on the database with a test value for the thumbnail of this frame
      (th/db-insert! :file-thumbnail
                     {:file-id (:file-id data)
                      :revn 2
                      :data "testvalue2"})

      (let [{:keys [result error] :as out} (th/query! data)]
        ;; (th/print-result! out)
        (t/is (nil? error))
        (t/is (= 4 (count result)))
        (t/is (= "testvalue2" (:data result)))
        (t/is (= 2 (:revn result))))

      ;; Then query the specific revn
      (let [{:keys [result error] :as out} (th/query! (assoc data :revn 1))]
        ;; (th/print-result! out)
        (t/is (nil? error))
        (t/is (= 4 (count result)))
        (t/is (= "testvalue1" (:data result)))
        (t/is (= 1 (:revn result)))))

    (t/testing "upsert file-thumbnail"
      (let [data {::th/type :upsert-file-thumbnail
                  :profile-id (:id prof)
                  :file-id (:id file)
                  :data "foobar"
                  :props {:baz 1}
                  :revn 2}
            {:keys [result error] :as out} (th/mutation! data)]
        ;; (th/print-result! out)
        (t/is (nil? error))
        (t/is (nil? result))))

    (t/testing "query last result"
      (let [{:keys [result error] :as out} (th/query! data)]
        ;; (th/print-result! out)
        (t/is (nil? error))
        (t/is (= 4 (count result)))
        (t/is (= "foobar" (:data result)))
        (t/is (= {:baz 1} (:props result)))
        (t/is (= 2 (:revn result)))))

    (t/testing "gc task"
      ;; make the file eligible for GC waiting 300ms (configured
      ;; timeout for testing)
      (th/sleep 300)

      ;; run the task again
      (let [task  (:app.tasks.file-gc/handler th/*system*)
            res   (task {})]
        (t/is (= 1 (:processed res))))

      ;; Then query the specific revn
      (let [{:keys [result error] :as out} (th/query! (assoc data :revn 1))]
        (t/is (= :not-found (th/ex-type error)))
        (t/is (= :file-thumbnail-not-found (th/ex-code error)))))
    ))


