;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.main.refs
  "A collection of derived refs."
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.common.geom.shapes :as gsh]
   [app.common.pages.helpers :as cph]
   [app.common.path.commands :as upc]
   [app.main.data.workspace.state-helpers :as wsh]
   [app.main.store :as st]
   [okulary.core :as l]))

;; ---- Global refs

(def route
  (l/derived :route st/state))

(def router
  (l/derived :router st/state))

(def message
  (l/derived :message st/state))

(def profile
  (l/derived :profile st/state))

(def teams
  (l/derived :teams st/state))

(def exception
  (l/derived :exception st/state))

(def threads-ref
  (l/derived :comment-threads st/state))

(def share-links
  (l/derived :share-links st/state))

(def export
  (l/derived :export st/state))

;; ---- Dashboard refs

(def dashboard-local
  (l/derived :dashboard-local st/state))

(def dashboard-fonts
  (l/derived :dashboard-fonts st/state))

(def dashboard-projects
  (l/derived :dashboard-projects st/state))

(def dashboard-files
  (l/derived :dashboard-files st/state))

(def dashboard-shared-files
  (l/derived :dashboard-shared-files st/state))

(def dashboard-search-result
  (l/derived :dashboard-search-result st/state))

(def dashboard-team-stats
  (l/derived :dashboard-team-stats st/state))

(def dashboard-team-members
  (l/derived :dashboard-team-members st/state))

(def dashboard-team-invitations
  (l/derived :dashboard-team-invitations st/state))

(def dashboard-selected-project
  (l/derived (fn [state]
               (dm/get-in state [:dashboard-local :selected-project]))
             st/state))

(def dashboard-selected-files
  (l/derived (fn [state]
               (let [get-file #(dm/get-in state [:dashboard-files %])
                     sim-file #(select-keys % [:id :name :project-id :is-shared])
                     selected (get-in state [:dashboard-local :selected-files])
                     xform    (comp (map get-file)
                                    (map sim-file))]
                 (->> (into #{} xform selected)
                      (d/index-by :id))))
             st/state =))

;; ---- Workspace refs

(def workspace-local
  (l/derived :workspace-local st/state))

(def workspace-global
  (l/derived :workspace-global st/state))

(def workspace-drawing
  (l/derived :workspace-drawing st/state))

;; TODO: rename to workspace-selected (?)
(def selected-shapes
  (l/derived wsh/lookup-selected st/state =))

(defn make-selected-ref
  [id]
  (l/derived #(contains? % id) selected-shapes))

(def export-in-progress?
  (l/derived :export-in-progress? export))

(def export-error?
  (l/derived :export-error? export))

(def export-progress
  (l/derived :export-progress export))

(def exports
  (l/derived :exports export))

(def export-detail-visibililty
  (l/derived :export-detail-visibililty export))

(def export-widget-visibililty
  (l/derived :export-widget-visibililty export))

(def export-health
  (l/derived :export-health export))

(def selected-zoom
  (l/derived :zoom workspace-local))

(def selected-drawing-tool
  (l/derived :tool workspace-drawing))

(def current-drawing-shape
  (l/derived :object workspace-drawing))

(def selected-edition
  (l/derived :edition workspace-local))

(def current-transform
  (l/derived :transform workspace-local))

(def options-mode
  (l/derived :options-mode workspace-local))

(def vbox
  (l/derived :vbox workspace-local))

(def current-hover
  (l/derived :hover workspace-local))

(def context-menu
  (l/derived :context-menu workspace-local))

(def current-hover-ids
  (l/derived :hover-ids context-menu))

(def selected-assets
  (l/derived :selected-assets workspace-global))

(def workspace-layout
  (l/derived :workspace-layout st/state))

(def snap-pixel?
  (l/derived #(contains? % :snap-pixel-grid) workspace-layout))

(def workspace-file
  "A ref to a striped vision of file (without data)."
  (l/derived (fn [state]
               (let [file (:workspace-file state)
                     data (:workspace-data state)]
                 (-> file
                     (dissoc :data)
                     (assoc :pages (:pages data)))))
             st/state =))

(def workspace-file-colors
  (l/derived (fn [state]
               (when-let [file (:workspace-data state)]
                 (->> (:colors file)
                      (d/mapm #(assoc %2 :file-id (:id file))))))
             st/state))

(def workspace-recent-colors
  (l/derived (fn [state]
               (dm/get-in state [:workspace-data :recent-colors] []))
             st/state))

(def workspace-recent-fonts
  (l/derived (fn [state]
               (dm/get-in state [:workspace-data :recent-fonts] []))
             st/state))

(def workspace-file-typography
  (l/derived (fn [state]
               (when-let [file (:workspace-data state)]
                 (:typographies file)))
             st/state))

(def workspace-project
  (l/derived :workspace-project st/state))

(def workspace-shared-files
  (l/derived :workspace-shared-files st/state))

(def workspace-local-library
  (l/derived (fn [state]
               (select-keys (:workspace-data state)
                            [:id
                             :colors
                             :media
                             :typographies
                             :components]))
             st/state =))

(def workspace-libraries
  (l/derived :workspace-libraries st/state))

(def workspace-presence
  (l/derived :workspace-presence st/state))

(def workspace-snap-data
  (l/derived :workspace-snap-data st/state))

(def workspace-page
  (l/derived (fn [state]
               (let [page-id (:current-page-id state)
                     data    (:workspace-data state)]
                 (dm/get-in data [:pages-index page-id])))
             st/state))

(def workspace-page-objects
  (l/derived wsh/lookup-page-objects st/state =))

(def workspace-modifiers
  (l/derived :workspace-modifiers st/state))

(def workspace-page-options
  (l/derived :options workspace-page))

(def workspace-frames
  (l/derived cph/get-frames workspace-page-objects =))

(def workspace-editor
  (l/derived :workspace-editor st/state))

(def workspace-editor-state
  (l/derived :workspace-editor-state st/state))

(defn object-by-id
  [id]
  (l/derived #(get % id) workspace-page-objects))

(defn objects-by-id
  [ids]
  (l/derived #(wsh/lookup-shapes % ids) st/state =))

(defn- set-content-modifiers [state]
  (fn [id shape]
    (let [content-modifiers (dm/get-in state [:workspace-local :edit-path id :content-modifiers])]
      (if (some? content-modifiers)
        (update shape :content upc/apply-content-modifiers content-modifiers)
        shape))))

(defn select-bool-children [id]
  (let [selector
        (fn [state]
          (let [objects   (wsh/lookup-page-objects state)
                modifiers (:workspace-modifiers state)
                children  (->> (cph/get-children-ids objects id)
                               (select-keys objects))]
            (as-> children $
              (gsh/merge-modifiers $ modifiers)
              (d/mapm (set-content-modifiers state) $))))]
    (l/derived selector st/state =)))

(def selected-data
  (l/derived #(let [selected (wsh/lookup-selected %)
                    objects (wsh/lookup-page-objects %)]
                (hash-map :selected selected
                          :objects objects))
             st/state =))

(defn is-child-selected?
  [id]
  (letfn [(selector [{:keys [selected objects]}]
            (let [children (cph/get-children-ids objects id)]
              (some #(contains? selected %) children)))]
    (l/derived selector selected-data =)))

(def selected-objects
  (letfn [(selector [{:keys [selected objects]}]
            (->> selected
                 (map #(get objects %))
                 (filterv (comp not nil?))))]
    (l/derived selector selected-data =)))

(def selected-shapes-with-children
  (letfn [(selector [{:keys [selected objects]}]
            (let [xform (comp (remove nil?)
                              (mapcat #(cph/get-children-ids objects %)))
                  shapes (into selected xform selected)]
              (mapv (d/getf objects) shapes)))]
    (l/derived selector selected-data =)))

(def workspace-focus-selected
  (l/derived :workspace-focus-selected st/state))

;; ---- Viewer refs

(def viewer-file
  (l/derived :viewer-file st/state))

(def viewer-project
  (l/derived :viewer-file st/state))

(def viewer-data
  (l/derived :viewer st/state))

(def viewer-state
  (l/derived :viewer st/state))

(def viewer-local
  (l/derived :viewer-local st/state))

(def comment-threads
  (l/derived :comment-threads st/state))

(def comments-local
  (l/derived :comments-local st/state))

(def users
  (l/derived :users st/state))

(def viewer-fullscreen?
  (l/derived (fn [state]
               (dm/get-in state [:viewer-local :fullscreen?]))
             st/state))
