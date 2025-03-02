;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.main.data.workspace.libraries
  (:require
   [app.common.data :as d]
   [app.common.geom.point :as gpt]
   [app.common.logging :as log]
   [app.common.pages :as cp]
   [app.common.pages.changes-builder :as pcb]
   [app.common.pages.helpers :as cph]
   [app.common.spec :as us]
   [app.common.spec.change :as spec.change]
   [app.common.spec.color :as spec.color]
   [app.common.spec.file :as spec.file]
   [app.common.spec.typography :as spec.typography]
   [app.common.uuid :as uuid]
   [app.main.data.messages :as dm]
   [app.main.data.workspace.changes :as dch]
   [app.main.data.workspace.common :as dwc]
   [app.main.data.workspace.groups :as dwg]
   [app.main.data.workspace.libraries-helpers :as dwlh]
   [app.main.data.workspace.state-helpers :as wsh]
   [app.main.data.workspace.undo :as dwu]
   [app.main.repo :as rp]
   [app.main.store :as st]
   [app.util.i18n :refer [tr]]
   [app.util.router :as rt]
   [app.util.time :as dt]
   [beicon.core :as rx]
   [potok.core :as ptk]))

;; Change this to :info :debug or :trace to debug this module, or :warn to reset to default
(log/set-level! :warn)

(defn- log-changes
  [changes file]
  (let [extract-change
        (fn [change]
          (let [shape (when (:id change)
                        (cond
                          (:page-id change)
                          (get-in file [:pages-index
                                        (:page-id change)
                                        :objects
                                        (:id change)])
                          (:component-id change)
                          (get-in file [:components
                                        (:component-id change)
                                        :objects
                                        (:id change)])
                          :else nil))

                prefix (if (:component-id change) "[C] " "[P] ")

                extract (cond-> {:type (:type change)
                                 :raw-change change}
                          shape
                          (assoc :shape (str prefix (:name shape)))
                          (:operations change)
                          (assoc :operations (:operations change)))]
            extract))]
    (map extract-change changes)))

(declare sync-file)

(defn set-assets-box-open
  [file-id box open?]
  (ptk/reify ::set-assets-box-open
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:workspace-global :assets-files-open file-id box] open?))))

(defn set-assets-group-open
  [file-id box path open?]
  (ptk/reify ::set-assets-group-open
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:workspace-global :assets-files-open file-id :groups box path] open?))))

(defn default-color-name [color]
  (or (:color color)
      (case (get-in color [:gradient :type])
        :linear (tr "workspace.gradients.linear")
        :radial (tr "workspace.gradients.radial"))))

(defn add-color
  [color]
  (let [id    (uuid/next)
        color (-> color
                  (assoc :id id)
                  (assoc :name (default-color-name color)))]
    (us/assert ::spec.color/color color)
    (ptk/reify ::add-color
      IDeref
      (-deref [_] color)

      ptk/WatchEvent
      (watch [it _ _]
        (let [changes (-> (pcb/empty-changes it)
                          (pcb/add-color color))]
          (rx/of #(assoc-in % [:workspace-local :color-for-rename] id)
                 (dch/commit-changes changes)))))))

(defn add-recent-color
  [color]
  (us/assert ::spec.color/recent-color color)
  (ptk/reify ::add-recent-color
    ptk/WatchEvent
    (watch [it _ _]
      (let [changes (-> (pcb/empty-changes it)
                        (pcb/add-recent-color color))]
        (rx/of (dch/commit-changes changes))))))

(def clear-color-for-rename
  (ptk/reify ::clear-color-for-rename
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:workspace-local :color-for-rename] nil))))

(defn update-color
  [color file-id]
  (us/assert ::spec.color/color color)
  (us/assert ::us/uuid file-id)
  (ptk/reify ::update-color
    ptk/WatchEvent
    (watch [it state _]
      (let [data        (get state :workspace-data)
            [path name] (cph/parse-path-name (:name color))
            color       (assoc color :path path :name name)
            changes     (-> (pcb/empty-changes it)
                            (pcb/with-library-data data)
                            (pcb/update-color color))]
        (rx/of (dwu/start-undo-transaction)
               (dch/commit-changes changes)
               (sync-file (:current-file-id state) file-id)
               (dwu/commit-undo-transaction))))))

(defn delete-color
  [{:keys [id] :as params}]
  (us/assert ::us/uuid id)
  (ptk/reify ::delete-color
    ptk/WatchEvent
    (watch [it state _]
      (let [data    (get state :workspace-data)
            changes (-> (pcb/empty-changes it)
                        (pcb/with-library-data data)
                        (pcb/delete-color id))]
        (rx/of (dch/commit-changes changes))))))

(defn add-media
  [media]
  (us/assert ::spec.file/media-object media)
  (ptk/reify ::add-media
    ptk/WatchEvent
    (watch [it _ _]
      (let [obj     (select-keys media [:id :name :width :height :mtype])
            changes (-> (pcb/empty-changes it)
                        (pcb/add-media obj))]
        (rx/of (dch/commit-changes changes))))))

(defn rename-media
  [id new-name]
  (us/assert ::us/uuid id)
  (us/assert ::us/string new-name)
  (ptk/reify ::rename-media
    ptk/WatchEvent
    (watch [it state _]
      (let [data        (get state :workspace-data)
            [path name] (cph/parse-path-name new-name)
            object      (get-in data [:media id])
            new-object  (assoc object :path path :name name)
            changes     (-> (pcb/empty-changes it)
                            (pcb/with-library-data data)
                            (pcb/update-media new-object))]
        (rx/of (dch/commit-changes changes))))))

(defn delete-media
  [{:keys [id] :as params}]
  (us/assert ::us/uuid id)
  (ptk/reify ::delete-media
    ptk/WatchEvent
    (watch [it state _]
      (let [data        (get state :workspace-data)
            changes (-> (pcb/empty-changes it)
                        (pcb/with-library-data data)
                        (pcb/delete-media id))]
        (rx/of (dch/commit-changes changes))))))

(defn add-typography
  ([typography] (add-typography typography true))
  ([typography edit?]
   (let [typography (update typography :id #(or % (uuid/next)))]
     (us/assert ::spec.typography/typography typography)
     (ptk/reify ::add-typography
       IDeref
       (-deref [_] typography)

       ptk/WatchEvent
       (watch [it _ _]
         (let [changes (-> (pcb/empty-changes it)
                           (pcb/add-typography typography))]
           (rx/of (dch/commit-changes changes)
                  #(cond-> %
                     edit?
                     (assoc-in [:workspace-global :rename-typography] (:id typography))))))))))

(defn update-typography
  [typography file-id]
  (us/assert ::spec.typography/typography typography)
  (us/assert ::us/uuid file-id)
  (ptk/reify ::update-typography
    ptk/WatchEvent
    (watch [it state _]
      (let [data    (get state :workspace-data)
            changes (-> (pcb/empty-changes it)
                        (pcb/with-library-data data)
                        (pcb/update-typography typography))]
        (rx/of (dwu/start-undo-transaction)
               (dch/commit-changes changes)
               (sync-file (:current-file-id state) file-id)
               (dwu/commit-undo-transaction))))))

(defn delete-typography
  [id]
  (us/assert ::us/uuid id)
  (ptk/reify ::delete-typography
    ptk/WatchEvent
    (watch [it state _]
      (let [data    (get state :workspace-data)
            changes (-> (pcb/empty-changes it)
                        (pcb/with-library-data data)
                        (pcb/delete-typography id))]
        (rx/of (dch/commit-changes changes))))))

(defn- add-component2
  "This is the second step of the component creation."
  [selected]
  (ptk/reify ::add-component2
    IDeref
    (-deref [_] {:num-shapes (count selected)})

    ptk/WatchEvent
    (watch [it state _]
      (let [file-id  (:current-file-id state)
            page-id  (:current-page-id state)
            objects  (wsh/lookup-page-objects state page-id)
            shapes   (dwg/shapes-for-grouping objects selected)]
        (when-not (empty? shapes)
          (let [[group _ changes]
                (dwlh/generate-add-component it shapes objects page-id file-id)]
            (when-not (empty? (:redo-changes changes))
              (rx/of (dch/commit-changes changes)
                     (dwc/select-shapes (d/ordered-set (:id group)))))))))))

(defn add-component
  "Add a new component to current file library, from the currently selected shapes.
  This operation is made in two steps, first one for calculate the
  shapes that will be part of the component and the second one with
  the component creation."
  []
  (ptk/reify ::add-component
    ptk/WatchEvent
    (watch [_ state _]
      (let [objects  (wsh/lookup-page-objects state)
            selected (->> (wsh/lookup-selected state)
                          (cph/clean-loops objects))]
        (rx/of (add-component2 selected))))))

(defn rename-component
  "Rename the component with the given id, in the current file library."
  [id new-name]
  (us/assert ::us/uuid id)
  (us/assert ::us/string new-name)
  (ptk/reify ::rename-component
    ptk/WatchEvent
    (watch [it state _]
      (let [data        (get state :workspace-data)
            [path name] (cph/parse-path-name new-name)

            update-fn
            (fn [component]
              ;; NOTE: we need to ensure the component exists,
              ;; because there are small posibilities of race
              ;; conditions with component deletion.
              (when component
                (-> component
                    (assoc :path path)
                    (assoc :name name)
                    (update :objects 
                            ;; Give the same name to the root shape
                            #(assoc-in % [id :name] name)))))

            changes (-> (pcb/empty-changes it)
                        (pcb/with-library-data data)
                        (pcb/update-component id update-fn))]

          (rx/of (dch/commit-changes changes))))))

(defn duplicate-component
  "Create a new component copied from the one with the given id."
  [{:keys [id] :as params}]
  (ptk/reify ::duplicate-component
    ptk/WatchEvent
    (watch [it state _]
      (let [libraries      (wsh/get-libraries state)
            component      (cph/get-component libraries id)
            all-components (-> state :workspace-data :components vals)
            unames         (into #{} (map :name) all-components)
            new-name       (dwc/generate-unique-name unames (:name component))

            [new-shape new-shapes _updated-shapes]
            (dwlh/duplicate-component component)

            changes (-> (pcb/empty-changes it nil) ;; no objects are changed
                        (pcb/with-objects nil)     ;; in the current page
                        (pcb/add-component (:id new-shape)
                                           (:path component)
                                           new-name
                                           new-shapes
                                           []))]

        (rx/of (dch/commit-changes changes))))))

(defn delete-component
  "Delete the component with the given id, from the current file library."
  [{:keys [id] :as params}]
  (us/assert ::us/uuid id)
  (ptk/reify ::delete-component
    ptk/WatchEvent
    (watch [it state _]
      (let [data        (get state :workspace-data)
            changes (-> (pcb/empty-changes it)
                        (pcb/with-library-data data)
                        (pcb/delete-component id))]

        (rx/of (dch/commit-changes changes))))))

(defn instantiate-component
  "Create a new shape in the current page, from the component with the given id
  in the given file library. Then selects the newly created instance."
  [file-id component-id position]
  (us/assert ::us/uuid file-id)
  (us/assert ::us/uuid component-id)
  (us/assert ::gpt/point position)
  (ptk/reify ::instantiate-component
    ptk/WatchEvent
    (watch [it state _]
      (let [page      (wsh/lookup-page state)
            libraries (wsh/get-libraries state)

            [new-shape changes]
            (dwlh/generate-instantiate-component it
                                                 file-id
                                                 component-id
                                                 position
                                                 page
                                                 libraries)]
        (rx/of (dch/commit-changes changes)
               (dwc/select-shapes (d/ordered-set (:id new-shape))))))))

(defn detach-component
  "Remove all references to components in the shape with the given id,
  and all its children, at the current page."
  [id]
  (us/assert ::us/uuid id)
  (ptk/reify ::detach-component
    ptk/WatchEvent
    (watch [it state _]
      (let [file      (wsh/get-local-file state)
            page-id   (get state :current-page-id)
            container (cph/get-container file :page page-id)

            changes   (-> (pcb/empty-changes it)
                          (pcb/with-container container)
                          (pcb/with-objects (:objects container))
                          (dwlh/generate-detach-instance container id))]

        (rx/of (dch/commit-changes changes))))))

(def detach-selected-components
  (ptk/reify ::detach-selected-components
    ptk/WatchEvent
    (watch [it state _]
      (let [page-id   (:current-page-id state)
            objects   (wsh/lookup-page-objects state page-id)
            file      (wsh/get-local-file state)
            container (cph/get-container file :page page-id)
            selected  (->> state
                           (wsh/lookup-selected)
                           (cph/clean-loops objects))

            changes (reduce
                      (fn [changes id]
                        (dwlh/generate-detach-instance changes container id))
                      (-> (pcb/empty-changes it)
                          (pcb/with-container container)
                          (pcb/with-objects objects))
                      selected)]

        (rx/of (dch/commit-changes changes))))))

(defn nav-to-component-file
  [file-id]
  (us/assert ::us/uuid file-id)
  (ptk/reify ::nav-to-component-file
    ptk/WatchEvent
    (watch [_ state _]
      (let [file         (get-in state [:workspace-libraries file-id])
            path-params  {:project-id (:project-id file)
                          :file-id (:id file)}
            query-params {:page-id (first (get-in file [:data :pages]))
                          :layout :assets}]
        (rx/of (rt/nav-new-window* {:rname :workspace
                                    :path-params path-params
                                    :query-params query-params}))))))

(defn ext-library-changed
  [file-id modified-at revn changes]
  (us/assert ::us/uuid file-id)
  (us/assert ::spec.change/changes changes)
  (ptk/reify ::ext-library-changed
    ptk/UpdateEvent
    (update [_ state]
      (-> state
          (update-in [:workspace-libraries file-id]
                     assoc :modified-at modified-at :revn revn)
          (d/update-in-when [:workspace-libraries file-id :data]
                            cp/process-changes changes)))))

(defn reset-component
  "Cancels all modifications in the shape with the given id, and all its children, in
  the current page. Set all attributes equal to the ones in the linked component,
  and untouched."
  [id]
  (us/assert ::us/uuid id)
  (ptk/reify ::reset-component
    ptk/WatchEvent
    (watch [it state _]
      (log/info :msg "RESET-COMPONENT of shape" :id (str id))
      (let [file      (wsh/get-local-file state)
            libraries (wsh/get-libraries state)

            page-id   (:current-page-id state)
            container (cph/get-container file :page page-id)

            changes
            (-> (pcb/empty-changes it)
                (pcb/with-container container)
                (pcb/with-objects (:objects container))
                (dwlh/generate-sync-shape-direct libraries container id true))]

        (log/debug :msg "RESET-COMPONENT finished" :js/rchanges (log-changes
                                                                 (:redo-changes changes)
                                                                   file))
          (rx/of (dch/commit-changes changes))))))

(defn update-component
  "Modify the component linked to the shape with the given id, in the
  current page, so that all attributes of its shapes are equal to the
  shape and its children. Also set all attributes of the shape
  untouched.

  NOTE: It's possible that the component to update is defined in an
  external library file, so this function may cause to modify a file
  different of that the one we are currently editing."
  [id]
  (us/assert ::us/uuid id)
  (ptk/reify ::update-component
    ptk/WatchEvent
    (watch [it state _]
      (log/info :msg "UPDATE-COMPONENT of shape" :id (str id))
      (let [page-id       (get state :current-page-id)

            local-file    (wsh/get-local-file state)
            libraries     (wsh/get-libraries state)

            container     (cph/get-container local-file :page page-id)
            shape         (cph/get-shape container id)

            changes
            (-> (pcb/empty-changes it)
                (pcb/with-container container)
                (dwlh/generate-sync-shape-inverse libraries container id))

            file-id   (:component-file shape)
            file      (wsh/get-file state file-id)

            xf-filter (comp
                       (filter :local-change?)
                       (map #(dissoc % :local-change?)))

            local-changes (-> changes
                              (update :redo-changes #(into [] xf-filter %))
                              (update :undo-changes #(into [] xf-filter %)))

            xf-remove (comp
                       (remove :local-change?)
                       (map #(dissoc % :local-change?)))

            nonlocal-changes (-> changes
                                 (update :redo-changes #(into [] xf-remove %))
                                 (update :undo-changes #(into [] xf-remove %)))]

        (log/debug :msg "UPDATE-COMPONENT finished"
                   :js/local-changes (log-changes
                                       (:redo-changes local-changes)
                                       file)
                   :js/nonlocal-changes (log-changes
                                          (:redo-changes nonlocal-changes)
                                          file))

        (rx/of
         (when (seq (:redo-changes local-changes))
           (dch/commit-changes (assoc local-changes
                                      :file-id (:id local-file))))
         (when (seq (:redo-changes nonlocal-changes))
           (dch/commit-changes (assoc nonlocal-changes
                                      :file-id file-id))))))))

(defn update-component-sync
  [shape-id file-id]
  (ptk/reify ::update-component-sync
    ptk/WatchEvent
    (watch [_ state _]
      (let [current-file-id (:current-file-id state)]
        (rx/of
         (dwu/start-undo-transaction)
         (update-component shape-id)
         (sync-file current-file-id file-id)
         (when (not= current-file-id file-id)
           (sync-file file-id file-id))
         (dwu/commit-undo-transaction))))))

(defn update-component-in-bulk
  [shapes file-id]
  (ptk/reify ::update-component-in-bulk
    ptk/WatchEvent
    (watch [_ _ _]
      (rx/concat
       (rx/of (dwu/start-undo-transaction))
       (rx/map #(update-component-sync (:id %) file-id) (rx/from shapes))
       (rx/of (dwu/commit-undo-transaction))))))

(declare sync-file-2nd-stage)

(defn sync-file
  "Synchronize the given file from the given library. Walk through all
  shapes in all pages in the file that use some color, typography or
  component of the library, and copy the new values to the shapes. Do
  it also for shapes inside components of the local file library."
  [file-id library-id]
  (us/assert ::us/uuid file-id)
  (us/assert ::us/uuid library-id)
  (ptk/reify ::sync-file
    ptk/UpdateEvent
    (update [_ state]
      (if (not= library-id (:current-file-id state))
        (d/assoc-in-when state [:workspace-libraries library-id :synced-at] (dt/now))
        state))

    ptk/WatchEvent
    (watch [it state _]
      (log/info :msg "SYNC-FILE"
                :file (dwlh/pretty-file file-id state)
                :library (dwlh/pretty-file library-id state))
      (let [file            (wsh/get-file state file-id)

            library-changes (reduce
                              pcb/concat-changes
                              (pcb/empty-changes it)
                              [(dwlh/generate-sync-library it file-id :components library-id state)
                               (dwlh/generate-sync-library it file-id :colors library-id state)
                               (dwlh/generate-sync-library it file-id :typographies library-id state)])
            file-changes    (reduce
                              pcb/concat-changes
                              (pcb/empty-changes it)
                              [(dwlh/generate-sync-file it file-id :components library-id state)
                               (dwlh/generate-sync-file it file-id :colors library-id state)
                               (dwlh/generate-sync-file it file-id :typographies library-id state)])

            changes         (pcb/concat-changes library-changes file-changes)]

        (log/debug :msg "SYNC-FILE finished" :js/rchanges (log-changes
                                                           (:redo-changes changes)
                                                           file))
        (rx/concat
         (rx/of (dm/hide-tag :sync-dialog))
         (when (seq (:redo-changes changes))
           (rx/of (dch/commit-changes (assoc changes ;; TODO a ver qué pasa con esto
                                             :file-id file-id))))
         (when (not= file-id library-id)
            ;; When we have just updated the library file, give some time for the
            ;; update to finish, before marking this file as synced.
            ;; TODO: look for a more precise way of syncing this.
            ;; Maybe by using the stream (second argument passed to watch)
            ;; to wait for the corresponding changes-committed and then proceed
            ;; with the :update-sync mutation.
           (rx/concat (rx/timer 3000)
                      (rp/mutation :update-sync
                                   {:file-id file-id
                                    :library-id library-id})))
         (when (seq (:redo-changes library-changes))
           (rx/of (sync-file-2nd-stage file-id library-id))))))))

(defn sync-file-2nd-stage
  "If some components have been modified, we need to launch another synchronization
  to update the instances of the changed components."
  ;; TODO: this does not work if there are multiple nested components. Only the
  ;;       first level will be updated.
  ;;       To solve this properly, it would be better to launch another sync-file
  ;;       recursively. But for this not to cause an infinite loop, we need to
  ;;       implement updated-at at component level, to detect what components have
  ;;       not changed, and then not to apply sync and terminate the loop.
  [file-id library-id]
  (us/assert ::us/uuid file-id)
  (us/assert ::us/uuid library-id)
  (ptk/reify ::sync-file-2nd-stage
    ptk/WatchEvent
    (watch [it state _]
      (log/info :msg "SYNC-FILE (2nd stage)"
                :file (dwlh/pretty-file file-id state)
                :library (dwlh/pretty-file library-id state))
      (let [file    (wsh/get-file state file-id)
            changes (reduce
                     pcb/concat-changes
                     (pcb/empty-changes it)
                     [(dwlh/generate-sync-file it file-id :components library-id state)
                      (dwlh/generate-sync-library it file-id :components library-id state)])]
        (when (seq (:redo-changes changes))
          (log/debug :msg "SYNC-FILE (2nd stage) finished" :js/rchanges (log-changes
                                                                         (:redo-changes changes)
                                                                         file))
          (rx/of (dch/commit-changes (assoc changes :file-id file-id))))))))

(def ignore-sync
  (ptk/reify ::ignore-sync
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:workspace-file :ignore-sync-until] (dt/now)))

    ptk/WatchEvent
    (watch [_ state _]
      (rp/mutation :ignore-sync
                   {:file-id (get-in state [:workspace-file :id])
                    :date (dt/now)}))))

(defn notify-sync-file
  [file-id]
  (us/assert ::us/uuid file-id)
  (ptk/reify ::notify-sync-file
    ptk/WatchEvent
    (watch [_ state _]
      (let [libraries-need-sync (filter #(> (:modified-at %) (:synced-at %))
                                        (vals (get state :workspace-libraries)))
            do-update #(do (apply st/emit! (map (fn [library]
                                                  (sync-file (:current-file-id state)
                                                             (:id library)))
                                                libraries-need-sync))
                           (st/emit! dm/hide))
            do-dismiss #(do (st/emit! ignore-sync)
                            (st/emit! dm/hide))]

        (rx/of (dm/info-dialog
                (tr "workspace.updates.there-are-updates")
                :inline-actions
                [{:label (tr "workspace.updates.update")
                  :callback do-update}
                 {:label (tr "workspace.updates.dismiss")
                  :callback do-dismiss}]
                :sync-dialog))))))

