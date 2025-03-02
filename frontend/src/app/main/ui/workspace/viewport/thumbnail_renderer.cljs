;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.main.ui.workspace.viewport.thumbnail-renderer
  (:require
   [app.common.math :as mth]
   [app.main.data.workspace.persistence :as dwp]
   [app.main.store :as st]
   [app.util.dom :as dom]
   [app.util.object :as obj]
   [app.util.timers :as timers]
   [beicon.core :as rx]
   [rumext.alpha :as mf]))

(mf/defc frame-thumbnail
  "Renders the canvas and image for a frame thumbnail and stores its value into the shape"
  [{:keys [shape background on-thumbnail-data on-frame-not-found]}]

  (let [thumbnail-img (mf/use-ref nil)
        thumbnail-canvas (mf/use-ref nil)

        {:keys [width height]} shape
        fixed-width (mth/clamp width 250 2000)
        fixed-height (/ (* height fixed-width) width)

        on-dom-rendered
        (mf/use-callback
         (mf/deps (:id shape))
         (fn [node]
           (when node
             (let [img-node (mf/ref-val thumbnail-img)]
               (timers/schedule-on-idle
                #(let [frame-node   (dom/get-element (str "shape-" (:id shape)))
                       thumb-node   (dom/query frame-node ".frame-thumbnail")
                       loading-node (dom/query frame-node "[data-loading=\"true\"]")]
                   (if (and (some? frame-node)
                            ;; Not render if the thumbnail is in display
                            (nil? thumb-node)
                            ;; Not render if some image is still loading
                            (nil? loading-node))
                     (let [frame-html (->  (js/XMLSerializer.)
                                           (.serializeToString frame-node))

                           ;; We need to wrap the group node into a SVG with a viewbox that matches the selrect of the frame
                           svg-node (.createElementNS js/document "http://www.w3.org/2000/svg" "svg")
                           _ (.setAttribute svg-node "version" "1.1")
                           _ (.setAttribute svg-node "viewBox" (str (:x shape) " " (:y shape) " " (:width shape) " " (:height shape)))
                           _ (.setAttribute svg-node "width" (:width shape))
                           _ (.setAttribute svg-node "height" (:height shape))
                           _ (unchecked-set svg-node "innerHTML" frame-html)
                           xml  (-> (js/XMLSerializer.)
                                    (.serializeToString svg-node)
                                    js/encodeURIComponent
                                    js/unescape
                                    js/btoa)
                           img-src (str "data:image/svg+xml;base64," xml)]
                       (obj/set! img-node "src" img-src))

                     (on-frame-not-found (:id shape)))))))))

        on-image-load
        (mf/use-callback
         (mf/deps on-thumbnail-data background)
         (fn []
           (let [canvas-node    (mf/ref-val thumbnail-canvas)
                 img-node       (mf/ref-val thumbnail-img)

                 canvas-context (.getContext canvas-node "2d")
                 canvas-width   (.-width canvas-node)
                 canvas-height  (.-height canvas-node)

                 _ (.clearRect canvas-context 0 0 canvas-width canvas-height)
                 _ (.rect canvas-context 0 0 canvas-width canvas-height)
                 _ (set! (.-fillStyle canvas-context) background)
                 _ (.fill canvas-context)
                 _ (.drawImage canvas-context img-node 0 0 canvas-width canvas-height)

                 data (.toDataURL canvas-node "image/jpg" 1)]
             (on-thumbnail-data data))))]

    [:div.frame-renderer {:ref on-dom-rendered
                          :style {:display "none"}}
     [:img.thumbnail-img
      {:ref thumbnail-img
       :width width
       :height height
       :on-load on-image-load}]

     [:canvas.thumbnail-canvas
      {:ref thumbnail-canvas
       :width fixed-width
       :height fixed-height}]]))

(mf/defc frame-renderer
  "Component in charge of creating thumbnails and storing them"
  {::mf/wrap-props false}
  [props]
  (let [objects    (obj/get props "objects")
        background (obj/get props "background")

        ;; Id of the current frame being rendered
        shape-id (mf/use-state nil)

        ;; This subject will emit a value every time there is a free "slot" to render
        ;; a thumbnail
        next (mf/use-memo #(rx/behavior-subject :next))

        render-frame
        (mf/use-callback
         (fn [frame-id]
           (reset! shape-id frame-id)))

        updates-stream
        (mf/use-memo
         #(let [update-events (rx/filter dwp/update-frame-thumbnail? st/stream)]
            (->> (rx/zip update-events next)
                 (rx/map first))))

        on-thumbnail-data
        (mf/use-callback
         (mf/deps @shape-id)
         (fn [data]
           (reset! shape-id nil)
           (timers/schedule
            (fn []
              (st/emit! (dwp/update-shape-thumbnail @shape-id data))
              (rx/push! next :next)))))

        on-frame-not-found
        (mf/use-callback
         (fn [frame-id]
           ;; If we couldn't find the frame maybe is still rendering. We push the event again
           ;; after a time
           (reset! shape-id nil)
           (rx/push! next :next)
           (timers/schedule-on-idle
            100
            (st/emitf (dwp/update-frame-thumbnail frame-id)))))]

    (mf/use-effect
     (mf/deps render-frame)
     (fn []
       (let [sub (->> updates-stream
                      (rx/subs #(render-frame (-> (deref %) :frame-id))))]

         #(rx/dispose! sub))))

    (mf/use-layout-effect
     (fn []
       (timers/schedule-on-idle
        #(st/emit! (dwp/watch-state-changes)))))

    (when (and (some? @shape-id) (contains? objects @shape-id))
      [:& frame-thumbnail {:key (str "thumbnail-" @shape-id)
                           :shape (get objects @shape-id)
                           :background background
                           :on-thumbnail-data on-thumbnail-data
                           :on-frame-not-found on-frame-not-found}])))
