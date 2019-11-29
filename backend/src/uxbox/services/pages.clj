;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2019 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.services.pages
  (:require
   [clojure.spec.alpha :as s]
   [promesa.core :as p]
   [uxbox.db :as db]
   [uxbox.util.spec :as us]
   [uxbox.services.core :as sv]
   [uxbox.util.sql :as sql]
   [uxbox.util.time :as dt]
   [uxbox.util.blob :as blob]
   [uxbox.util.uuid :as uuid]))

;; --- Helpers & Specs

(declare decode-row)

;; TODO: validate `:data` and `:metadata`

(s/def ::id ::us/uuid)
(s/def ::name ::us/string)
(s/def ::data any?)
(s/def ::user ::us/uuid)
(s/def ::project-id ::us/uuid)
(s/def ::metadata any?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; --- Query: Pages by Project

(s/def ::pages-by-project
  (s/keys :req-un [::user ::project-id]))

(sv/defquery ::pages-by-project
  [{:keys [user project-id] :as params}]
  (let [sql "select pg.*,
                    pg.data,
                    pg.metadata
               from pages as pg
              where pg.user_id = $2
                and pg.project_id = $1
                and pg.deleted_at is null
              order by pg.created_at asc;"]
    (-> (db/query db/pool [sql project-id user])
        (p/then #(mapv decode-row %)))))

;; --- Query: Page by Id

(s/def ::page
  (s/keys :req-un [::user ::id]))

(sv/defquery ::page
  [{:keys [user id] :as params}]
  (let [sql "select pg.*,
                    pg.data,
                    pg.metadata
               from pages as pg
              where pg.user_id = $2
                and pg.id = $1
                and pg.deleted_at is null"]
    (-> (db/query-one db/pool [sql id user])
        (p/then' decode-row))))

;; --- Query: Page History

(s/def ::page-id ::us/uuid)
(s/def ::max ::us/integer)
(s/def ::pinned ::us/boolean)
(s/def ::since ::us/integer)

(s/def ::page-history
  (s/keys :req-un [::page-id ::user]
          :opt-un [::max ::pinned ::since]))

(sv/defquery ::page-history
  [{:keys [page-id user since max pinned] :or {since Long/MAX_VALUE max 10}}]
  (let [sql (-> (sql/from ["pages_history" "ph"])
                (sql/select "ph.*")
                (sql/where ["ph.user_id = ?" user]
                           ["ph.page_id = ?" page-id]
                           ["ph.version < ?" since]
                           (when pinned
                             ["ph.pinned = ?" true]))
                (sql/order "ph.version desc")
                (sql/limit max))]
    (-> (db/query db/pool (sql/fmt sql))
        (p/then (partial mapv decode-row)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; --- Mutation: Create Page

(s/def ::create-page
  (s/keys :req-un [::data ::user ::project-id ::name ::metadata]
          :opt-un [::id]))

(sv/defmutation ::create-page
  [{:keys [id user project-id name data metadata]}]
  (let [sql "insert into pages (id, user_id, project_id, name, data, metadata)
             values ($1, $2, $3, $4, $5, $6) returning *"
        id   (or id (uuid/next))
        data (blob/encode data)
        mdata (blob/encode metadata)]
    (-> (db/query-one db/pool [sql id user project-id name data mdata])
        (p/then' decode-row))))


;; --- Mutation: Update Page

(s/def ::update-page
  (s/keys :req-un [::data ::user ::project-id ::name ::data ::metadata ::id]))

(letfn [(select-for-update [conn id]
          (let [sql "select p.id, p.version
                       from pages as p
                      where p.id = $1
                        and deleted_at is null
                        for update;"]
            (-> (db/query-one conn [sql id])
                (p/then' sv/raise-not-found-if-nil))))

        (update-page [conn {:keys [id name version data metadata user]}]
          (let [sql "update pages
                        set name = $1,
                            version = $2,
                            data = $3,
                            metadata = $4
                      where id = $5
                        and user_id = $6"]
            (-> (db/query-one conn [sql name version data metadata id user])
                (p/then' sv/constantly-nil))))

        (update-history [conn {:keys [user id version data metadata]}]
          (let [sql "insert into pages_history (user_id, page_id, version, data, metadata)
                     values ($1, $2, $3, $4, $5)"]
            (-> (db/query-one conn [sql user id version data metadata])
                (p/then' sv/constantly-nil))))]

  (sv/defmutation ::update-page
    [{:keys [id data metadata] :as params}]
    (db/with-atomic [conn db/pool]
      (-> (select-for-update conn id)
          (p/then (fn [{:keys [id version]}]
                    (let [data (blob/encode data)
                          mdata (blob/encode metadata)
                          version (inc version)
                          params (assoc params
                                        :id id
                                        :version version
                                        :data data
                                        :metadata mdata)]
                      (p/do! (update-page conn params)
                             (update-history conn params)
                             (select-keys params [:id :version])))))))))

;; --- Mutation: Update Page Metadata

(s/def ::update-page-metadata
  (s/keys :req-un [::user ::project-id ::name ::metadata ::id]))

(sv/defmutation ::update-page-metadata
  [{:keys [id user project-id name metadata]}]
  (let [sql "update pages
                set name = $3,
                    metadata = $4
              where id = $1
                and user_id = $2
                and deleted_at is null
             returning *"
        mdata (blob/encode metadata)]
    (-> (db/query-one db/pool [sql id user name mdata])
        (p/then' decode-row))))

;; --- Mutation: Delete Page

(s/def ::delete-page
  (s/keys :req-un [::user ::id]))

(sv/defmutation ::delete-page
  [{:keys [id user]}]
  (let [sql "update pages
                set deleted_at = clock_timestamp()
              where id = $1
                and user_id = $2
                and deleted_at is null
             returning id"]
    (-> (db/query-one db/pool [sql id user])
        (p/then sv/raise-not-found-if-nil)
        (p/then sv/constantly-nil))))

;; ;; --- Update Page History

;; (defn update-page-history
;;   [conn {:keys [user id label pinned]}]
;;   (let [sqlv (sql/update-page-history {:user user
;;                                        :id id
;;                                        :label label
;;                                        :pinned pinned})]
;;     (some-> (db/fetch-one conn sqlv)
;;             (decode-row))))

;; (s/def ::label ::us/string)
;; (s/def ::update-page-history
;;   (s/keys :req-un [::user ::id ::pinned ::label]))

;; (sv/defmutation :update-page-history
;;   {:doc "Update page history"
;;    :spec ::update-page-history}
;;   [params]
;;   (with-open [conn (db/connection)]
;;     (update-page-history conn params)))

;; --- Helpers

(defn- decode-row
  [{:keys [data metadata] :as row}]
  (when row
    (cond-> row
      data (assoc :data (blob/decode data))
      metadata (assoc :metadata (blob/decode metadata)))))

;; select pg.* from pages as pg
;;  where pg.id = :id
;;    and pg.deleted_at is null;

;; (defn get-page-by-id
;;   [conn id]
;;   (s/assert ::us/id id)
;;   (let [sqlv (sql/get-page-by-id {:id id})]
;;     (some-> (db/fetch-one conn sqlv)
;;             (decode-row))))
