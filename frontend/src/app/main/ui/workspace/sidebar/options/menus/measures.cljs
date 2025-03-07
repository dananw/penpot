;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.main.ui.workspace.sidebar.options.menus.measures
  (:require
   [app.common.data :as d]
   [app.common.geom.shapes :as gsh]
   [app.common.spec.radius :as ctr]
   [app.main.data.workspace :as udw]
   [app.main.data.workspace.changes :as dch]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.components.dropdown :refer [dropdown]]
   [app.main.ui.components.numeric-input :refer [numeric-input]]
   [app.main.ui.icons :as i]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [tr]]
   [clojure.set :refer [union]]
   [rumext.alpha :as mf]))

(def measure-attrs
  [:proportion-lock
   :width :height
   :x :y
   :rotation
   :rx :ry
   :r1 :r2 :r3 :r4
   :selrect])

(def ^:private type->options
  {:bool    #{:size :position :rotation}
   :circle  #{:size :position :rotation}
   :frame   #{:presets :size :position :radius}
   :group   #{:size :position :rotation}
   :image   #{:size :position :rotation :radius}
   :path    #{:size :position :rotation}
   :rect    #{:size :position :rotation :radius}
   :svg-raw #{:size :position :rotation}
   :text    #{:size :position :rotation}})

(declare +size-presets+)

;; -- User/drawing coords
(mf/defc measures-menu
  [{:keys [ids ids-with-children values type all-types shape] :as props}]
         
  (let [options (if (= type :multiple)
                  (reduce #(union %1 %2) (map #(get type->options %) all-types))
                  (get type->options type))

        ids-with-children (or ids-with-children ids)

        old-shapes (if (= type :multiple)
                     (deref (refs/objects-by-id ids))
                     [shape])
        frames (map #(deref (refs/object-by-id (:frame-id %))) old-shapes)

        shapes (as-> old-shapes $
                 (map gsh/transform-shape $)
                 (map gsh/translate-to-frame $ frames))

        values (let [{:keys [x y]} (-> shapes first :points gsh/points->selrect)]
                 (cond-> values
                   (not= (:x values) :multiple) (assoc :x x)
                   (not= (:y values) :multiple) (assoc :y y)))

        values (let [{:keys [width height]} (-> shapes first :selrect)]
                 (cond-> values
                   (not= (:width values) :multiple) (assoc :width width)
                   (not= (:height values) :multiple) (assoc :height height)))

        values (let [{:keys [rotation]} (-> shapes first)]
                 (cond-> values
                   (not= (:rotation values) :multiple) (assoc :rotation rotation)))

        proportion-lock (:proportion-lock values)

        show-presets-dropdown? (mf/use-state false)

        radius-mode      (ctr/radius-mode values)
        all-equal?       (ctr/all-equal? values)
        radius-multi?    (mf/use-state nil)
        radius-input-ref (mf/use-ref nil)


        on-preset-selected
        (fn [width height]
          (st/emit! (udw/update-dimensions ids :width width)
                    (udw/update-dimensions ids :height height)))

        on-orientation-clicked
        (fn [orientation]
          (let [width (:width values)
                height (:height values)
                new-width (if (= orientation :horiz) (max width height) (min width height))
                new-height (if (= orientation :horiz) (min width height) (max width height))]
            (st/emit! (udw/update-dimensions ids :width new-width)
                      (udw/update-dimensions ids :height new-height))))

        on-size-change
        (mf/use-callback
         (mf/deps ids)
         (fn [value attr]
           (st/emit! (udw/update-dimensions ids attr value))))

        on-proportion-lock-change
        (mf/use-callback
         (mf/deps ids)
         (fn [_]
           (let [new-lock (if (= proportion-lock :multiple) true (not proportion-lock))]
             (run! #(st/emit! (udw/set-shape-proportion-lock % new-lock)) ids))))

        do-position-change
        (mf/use-callback
         (mf/deps ids)
         (fn [shape' frame' value attr]
           (let [to (+ value (attr frame'))]
             (st/emit! (udw/update-position (:id shape') {attr to})))))

        on-position-change
        (mf/use-callback
         (mf/deps ids)
         (fn [value attr]
           (doall (map #(do-position-change %1 %2 value attr) shapes frames))))

        on-rotation-change
        (mf/use-callback
         (mf/deps ids)
         (fn [value]
           (st/emit! (udw/increase-rotation ids value))))

        change-radius
        (mf/use-callback
          (mf/deps ids-with-children)
          (fn [update-fn]
            (dch/update-shapes ids-with-children
                               (fn [shape]
                                 (if (ctr/has-radius? shape)
                                   (update-fn shape)
                                   shape)))))

        on-switch-to-radius-1
        (mf/use-callback
         (mf/deps ids)
         (fn [_value]
           (if all-equal?
             (st/emit! (change-radius ctr/switch-to-radius-1))
             (reset! radius-multi? true))))

        on-switch-to-radius-4
        (mf/use-callback
         (mf/deps ids)
         (fn [_value]
           (st/emit! (change-radius ctr/switch-to-radius-4))
           (reset! radius-multi? false)))

        on-radius-1-change
        (mf/use-callback
         (mf/deps ids)
         (fn [value]
           (st/emit! (change-radius #(ctr/set-radius-1 % value)))))

        on-radius-multi-change
        (mf/use-callback
         (mf/deps ids)
         (fn [event]
           (let [value (-> event dom/get-target dom/get-value d/parse-integer)]
             (when (some? value)
               (st/emit! (change-radius ctr/switch-to-radius-1)
                         (change-radius #(ctr/set-radius-1 % value)))
               (reset! radius-multi? false)))))

        on-radius-4-change
        (mf/use-callback
         (mf/deps ids)
         (fn [value attr]
           (st/emit! (change-radius #(ctr/set-radius-4 % attr value)))))

        on-width-change #(on-size-change % :width)
        on-height-change #(on-size-change % :height)
        on-pos-x-change #(on-position-change % :x)
        on-pos-y-change #(on-position-change % :y)
        on-radius-r1-change #(on-radius-4-change % :r1)
        on-radius-r2-change #(on-radius-4-change % :r2)
        on-radius-r3-change #(on-radius-4-change % :r3)
        on-radius-r4-change #(on-radius-4-change % :r4)

        select-all #(-> % (dom/get-target) (.select))]
    
        (mf/use-layout-effect
         (mf/deps radius-mode @radius-multi?)
         (fn []
           (when (and (= radius-mode :radius-1)
                      (= @radius-multi? false))
             ;; when going back from radius-multi to normal radius-1,
             ;; restore focus to the newly created numeric-input
             (let [radius-input (mf/ref-val radius-input-ref)]
               (dom/focus! radius-input)))))

    [:*
     [:div.element-set
      [:div.element-set-content

       ;; FRAME PRESETS
       (when (and (options :presets)
                  (or (nil? all-types) (= (count all-types) 1))) ;; Dont' show presets if multi selected
         [:div.row-flex                                          ;; some frames and some non frames
          [:div.presets.custom-select.flex-grow {:on-click #(reset! show-presets-dropdown? true)}
           [:span (tr "workspace.options.size-presets")]
           [:span.dropdown-button i/arrow-down]
           [:& dropdown {:show @show-presets-dropdown?
                         :on-close #(reset! show-presets-dropdown? false)}
            [:ul.custom-select-dropdown
             (for [size-preset +size-presets+]
               (if-not (:width size-preset)
                 [:li.dropdown-label {:key (:name size-preset)}
                  [:span (:name size-preset)]]
                 [:li {:key (:name size-preset)
                       :on-click #(on-preset-selected (:width size-preset) (:height size-preset))}
                  (:name size-preset)
                  [:span (:width size-preset) " x " (:height size-preset)]]))]]]
          [:span.orientation-icon {:on-click #(on-orientation-clicked :vert)} i/size-vert]
          [:span.orientation-icon {:on-click #(on-orientation-clicked :horiz)} i/size-horiz]])

       ;; WIDTH & HEIGHT
       (when (options :size)
         [:div.row-flex
          [:span.element-set-subtitle (tr "workspace.options.size")]
          [:div.input-element.width {:title (tr "workspace.options.width")}
           [:> numeric-input {:min 0.01
                              :no-validate true
                              :placeholder "--"
                              :on-click select-all
                              :on-change on-width-change
                              :value (:width values)}]]

          [:div.input-element.height {:title (tr "workspace.options.height")}
           [:> numeric-input {:min 0.01
                              :no-validate true
                              :placeholder "--"
                              :on-click select-all
                              :on-change on-height-change
                              :value (:height values)}]]

          [:div.lock-size {:class (dom/classnames
                                   :selected (true? proportion-lock)
                                   :disabled (= proportion-lock :multiple))
                           :on-click on-proportion-lock-change}
           (if proportion-lock
             i/lock
             i/unlock)]])

       ;; POSITION
       (when (options :position)
         [:div.row-flex
          [:span.element-set-subtitle (tr "workspace.options.position")]
          [:div.input-element.Xaxis {:title (tr "workspace.options.x")}
           [:> numeric-input {:no-validate true
                              :placeholder "--"
                              :on-click select-all
                              :on-change on-pos-x-change
                              :value (:x values)}]]
          [:div.input-element.Yaxis {:title (tr "workspace.options.y")}
           [:> numeric-input {:no-validate true
                              :placeholder "--"
                              :on-click select-all
                              :on-change on-pos-y-change
                              :value (:y values)}]]])

       ;; ROTATION
       (when (options :rotation)
         [:div.row-flex
          [:span.element-set-subtitle (tr "workspace.options.rotation")]
          [:div.input-element.degrees {:title (tr "workspace.options.rotation")}
           [:> numeric-input
            {:no-validate true
             :min 0
             :max 359
             :default 0
             :data-wrap true
             :placeholder "--"
             :on-click select-all
             :on-change on-rotation-change
             :value (:rotation values)}]]])

       ;; RADIUS
       (when (options :radius)
         [:div.row-flex
          [:div.radius-options
           [:div.radius-icon.tooltip.tooltip-bottom
            {:class (dom/classnames
                     :selected (or (= radius-mode :radius-1) @radius-multi?))
             :alt (tr "workspace.options.radius.all-corners")
             :on-click on-switch-to-radius-1}
            i/radius-1]
           [:div.radius-icon.tooltip.tooltip-bottom
            {:class (dom/classnames
                     :selected (and (= radius-mode :radius-4) (not @radius-multi?)))
             :alt (tr "workspace.options.radius.single-corners")
             :on-click on-switch-to-radius-4}
            i/radius-4]]

          (cond
            (= radius-mode :radius-1)
            [:div.input-element.mini {:title (tr "workspace.options.radius")}
             [:> numeric-input
              {:placeholder "--"
               :ref radius-input-ref
               :min 0
               :on-click select-all
               :on-change on-radius-1-change
               :value (:rx values)}]]

            @radius-multi?
            [:div.input-element.mini {:title (tr "workspace.options.radius")}
             [:input.input-text
              {:type "number"
               :placeholder "--"
               :min 0
               :on-click select-all
               :on-change on-radius-multi-change
               :value ""}]]

            (= radius-mode :radius-4)
            [:*
             [:div.input-element.mini {:title (tr "workspace.options.radius")}
              [:> numeric-input
               {:placeholder "--"
                :min 0
                :on-click select-all
                :on-change on-radius-r1-change
                :value (:r1 values)}]]

             [:div.input-element.mini {:title (tr "workspace.options.radius")}
              [:> numeric-input
               {:placeholder "--"
                :min 0
                :on-click select-all
                :on-change on-radius-r2-change
                :value (:r2 values)}]]

             [:div.input-element.mini {:title (tr "workspace.options.radius")}
              [:> numeric-input
               {:placeholder "--"
                :min 0
                :on-click select-all
                :on-change on-radius-r3-change
                :value (:r3 values)}]]

             [:div.input-element.mini {:title (tr "workspace.options.radius")}
              [:> numeric-input
               {:placeholder "--"
                :min 0
                :on-click select-all
                :on-change on-radius-r4-change
                :value (:r4 values)}]]])])]]]))

     (def +size-presets+
       [{:name "APPLE"}
        {:name "iPhone 12/12 Pro"
         :width 390
         :height 844}
        {:name "iPhone 12 Mini"
         :width 360
         :height 780}
        {:name "iPhone 12 Pro Max"
         :width 428
         :height 926}
        {:name "iPhone X/XS/11 Pro"
         :width 375
         :height 812}
        {:name "iPhone XS Max/XR/11"
         :width 414
         :height 896}
        {:name "iPhone 6/7/8 Plus"
         :width 414
         :height 736}
        {:name "iPhone 6/7/8/SE2"
         :width 375
         :height 667}
        {:name "iPhone 5/SE"
         :width 320
         :height 568}
        {:name "iPad"
         :width 768
         :height 1024}
        {:name "iPad Pro 10.5in"
         :width 834
         :height 1112}
        {:name "iPad Pro 12.9in"
         :width 1024
         :height 1366}
        {:name "Watch 44mm"
         :width 368
         :height 448}
        {:name "Watch 42mm"
         :width 312
         :height 390}
        {:name "Watch 40mm"
         :width 324
         :height 394}
        {:name "Watch 38mm"
         :width 272
         :height 340}

        {:name "ANDROID"}
        {:name "Mobile"
         :width 360
         :height 640}
        {:name "Tablet"
         :width 768
         :height 1024}
        {:name "Google Pixel 4a/5"
         :width 393
         :height 851}
        {:name "Samsung Galaxy S20+"
         :width 384
         :height 854}
        {:name "Samsung Galaxy A71/A51"
         :width 412
         :height 914}

        {:name "MICROSOFT"}
        {:name "Surface Pro 3"
         :width 1440
         :height 960}
        {:name "Surface Pro 4/5/6/7"
         :width 1368
         :height 912}

        {:name "ReMarkable"}
        {:name "Remarkable 2"
         :width 840
         :height 1120}

        {:name "WEB"}
        {:name "Web 1280"
         :width 1280
         :height 800}
        {:name "Web 1366"
         :width 1366
         :height 768}
        {:name "Web 1024"
         :width 1024
         :height 768}
        {:name "Web 1920"
         :width 1920
         :height 1080}

        {:name "PRINT (96dpi)"}
        {:name "A0"
         :width 3179
         :height 4494}
        {:name "A1"
         :width 2245
         :height 3179}
        {:name "A2"
         :width 1587
         :height 2245}
        {:name "A3"
         :width 1123
         :height 1587}
        {:name "A4"
         :width 794
         :height 1123}
        {:name "A5"
         :width 559
         :height 794}
        {:name "A6"
         :width 397
         :height 559}
        {:name "Letter"
         :width 816
         :height 1054}
        {:name "DIN Lang"
         :width 835
         :height 413}

        {:name "SOCIAL MEDIA"}
        {:name "Instagram profile"
         :width 320
         :height 320}
        {:name "Instagram post"
         :width 1080
         :height 1080}
        {:name "Instagram story"
         :width 1080
         :height 1920}
        {:name "Facebook profile"
         :width 720
         :height 720}
        {:name "Facebook cover"
         :width 820
         :height 312}
        {:name "Facebook post"
         :width 1200
         :height 630}
        {:name "LinkedIn profile"
         :width 400
         :height 400}
        {:name "LinkedIn cover"
         :width 1584
         :height 396}
        {:name "LinkedIn post"
         :width 1200
         :height 627}
        {:name "Twitter profile"
         :width 400
         :height 400}
        {:name "Twitter header"
         :width 1500
         :height 500}
        {:name "Twitter post"
         :width 1024
         :height 512}
        {:name "YouTube profile"
         :width 800
         :height 800}
        {:name "YouTube banner"
         :width 2560
         :height 1440}
        {:name "YouTube thumb"
         :width 1280
         :height 720}])
