;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) UXBOX Labs SL

(ns app.main.ui.icons
  (:refer-clojure :exclude [import mask])
  (:require-macros [app.main.ui.icons :refer [icon-xref]])
  (:require [rumext.alpha :as mf]))

;; Keep the list of icons sorted

(def action (icon-xref :action))
(def actions (icon-xref :actions))
(def align-bottom (icon-xref :align-bottom))
(def align-middle (icon-xref :align-middle))
(def align-top (icon-xref :align-top))
(def alignment (icon-xref :alignment))
(def animate-down (icon-xref :animate-down))
(def animate-left (icon-xref :animate-left))
(def animate-right (icon-xref :animate-right))
(def animate-up (icon-xref :animate-up))
(def arrow-down (icon-xref :arrow-down))
(def arrow-end (icon-xref :arrow-end))
(def arrow-slide (icon-xref :arrow-slide))
(def artboard (icon-xref :artboard))
(def at (icon-xref :at))
(def auto-fix (icon-xref :auto-fix))
(def auto-height (icon-xref :auto-height))
(def auto-width (icon-xref :auto-width))
(def bool-difference (icon-xref :boolean-difference))
(def bool-exclude (icon-xref :boolean-exclude))
(def bool-flatten (icon-xref :boolean-flatten))
(def bool-intersection (icon-xref :boolean-intersection))
(def bool-union (icon-xref :boolean-union))
(def box (icon-xref :box))
(def chain (icon-xref :chain))
(def chat (icon-xref :chat))
(def checkbox-checked (icon-xref :checkbox-checked))
(def checkbox-unchecked (icon-xref :checkbox-unchecked))
(def checkbox-intermediate (icon-xref :checkbox-intermediate))
(def circle (icon-xref :circle))
(def close (icon-xref :close))
(def code (icon-xref :code))
(def component (icon-xref :component))
(def copy (icon-xref :copy))
(def curve (icon-xref :curve))
(def cross (icon-xref :cross))
(def download (icon-xref :download))
(def easing-linear (icon-xref :easing-linear))
(def easing-ease (icon-xref :easing-ease))
(def easing-ease-in (icon-xref :easing-ease-in))
(def easing-ease-out (icon-xref :easing-ease-out))
(def easing-ease-in-out (icon-xref :easing-ease-in-out))
(def exclude (icon-xref :exclude))
(def exit (icon-xref :exit))
(def export (icon-xref :export))
(def eye (icon-xref :eye))
(def eye-closed (icon-xref :eye-closed))
(def file-html (icon-xref :file-html))
(def file-svg (icon-xref :file-svg))
(def fill (icon-xref :fill))
(def folder (icon-xref :folder))
(def folder-zip (icon-xref :folder-zip))
(def full-screen (icon-xref :full-screen))
(def full-screen-off (icon-xref :full-screen-off))
(def grid (icon-xref :grid))
(def grid-snap (icon-xref :grid-snap))
(def help (icon-xref :help))
(def icon-empty (icon-xref :icon-empty))
(def icon-filter (icon-xref :filter))
(def icon-list (icon-xref :icon-list))
(def icon-lock (icon-xref :icon-lock))
(def icon-set (icon-xref :icon-set))
(def icon-verify (icon-xref :icon-verify))
(def image (icon-xref :image))
(def import (icon-xref :import))
(def infocard (icon-xref :infocard))
(def interaction (icon-xref :interaction))
(def layers (icon-xref :layers))
(def letter-spacing (icon-xref :letter-spacing))
(def libraries (icon-xref :libraries))
(def library (icon-xref :library))
(def line (icon-xref :line))
(def line-height (icon-xref :line-height))
(def listing-enum (icon-xref :listing-enum))
(def listing-thumbs (icon-xref :listing-thumbs))
(def loader (icon-xref :loader))
(def lock (icon-xref :lock))
(def logo (icon-xref :penpot-logo))
(def logo-icon (icon-xref :penpot-logo-icon))
(def logout (icon-xref :logout))
(def lowercase (icon-xref :lowercase))
(def mail (icon-xref :mail))
(def mask (icon-xref :mask))
(def minus (icon-xref :minus))
(def move (icon-xref :move))
(def msg-error (icon-xref :msg-error))
(def msg-info (icon-xref :msg-info))
(def msg-success (icon-xref :msg-success))
(def msg-warning (icon-xref :msg-warning))
(def navigate (icon-xref :navigate))
(def nodes-add (icon-xref :nodes-add))
(def nodes-corner (icon-xref :nodes-corner))
(def nodes-curve (icon-xref :nodes-curve))
(def nodes-join (icon-xref :nodes-join))
(def nodes-merge (icon-xref :nodes-merge))
(def nodes-remove (icon-xref :nodes-remove))
(def nodes-separate (icon-xref :nodes-separate))
(def nodes-snap (icon-xref :nodes-snap))
(def organize (icon-xref :organize))
(def palette (icon-xref :palette))
(def pen (icon-xref :pen))
(def pencil (icon-xref :pencil))
(def picker (icon-xref :picker))
(def picker-harmony (icon-xref :picker-harmony))
(def picker-hsv (icon-xref :picker-hsv))
(def picker-ramp (icon-xref :picker-ramp))
(def pin (icon-xref :pin))
(def pin-fill (icon-xref :pin-fill))
(def play (icon-xref :play))
(def plus (icon-xref :plus))
(def pointer-inner (icon-xref :pointer-inner))
(def position-bottom-center (icon-xref :position-bottom-center))
(def position-bottom-left (icon-xref :position-bottom-left))
(def position-bottom-right (icon-xref :position-bottom-right))
(def position-center (icon-xref :position-center))
(def position-top-center (icon-xref :position-top-center))
(def position-top-left (icon-xref :position-top-left))
(def position-top-right (icon-xref :position-top-right))
(def radius (icon-xref :radius))
(def radius-1 (icon-xref :radius-1))
(def radius-4 (icon-xref :radius-4))
(def recent (icon-xref :recent))
(def redo (icon-xref :redo))
(def rotate (icon-xref :rotate))
(def ruler (icon-xref :ruler))
(def ruler-tool (icon-xref :ruler-tool))
(def search (icon-xref :search))
(def shape-halign-center (icon-xref :shape-halign-center))
(def shape-halign-left (icon-xref :shape-halign-left))
(def shape-halign-right (icon-xref :shape-halign-right))
(def shape-hdistribute (icon-xref :shape-hdistribute))
(def shape-valign-bottom (icon-xref :shape-valign-bottom))
(def shape-valign-center (icon-xref :shape-valign-center))
(def shape-valign-top (icon-xref :shape-valign-top))
(def shape-vdistribute (icon-xref :shape-vdistribute))
(def size-horiz (icon-xref :size-horiz))
(def size-vert (icon-xref :size-vert))
(def sort-ascending (icon-xref :sort-ascending))
(def sort-descending (icon-xref :sort-descending))
(def strikethrough (icon-xref :strikethrough))
(def stroke (icon-xref :stroke))
(def switch (icon-xref :switch))
(def text (icon-xref :text))
(def text-align-center (icon-xref :text-align-center))
(def text-align-justify (icon-xref :text-align-justify))
(def text-align-left (icon-xref :text-align-left))
(def text-align-right (icon-xref :text-align-right))
(def text-direction-ltr (icon-xref :text-direction-ltr))
(def text-direction-rtl (icon-xref :text-direction-rtl))
(def tick (icon-xref :tick))
(def titlecase (icon-xref :titlecase))
(def toggle (icon-xref :toggle))
(def trash (icon-xref :trash))
(def tree (icon-xref :tree))
(def unchain (icon-xref :unchain))
(def underline (icon-xref :underline))
(def undo (icon-xref :undo))
(def ungroup (icon-xref :ungroup))
(def unlock (icon-xref :unlock))
(def uppercase (icon-xref :uppercase))
(def user (icon-xref :user))

(def brand-openid (icon-xref :brand-openid))
(def brand-github (icon-xref :brand-github))
(def brand-gitlab (icon-xref :brand-gitlab))
(def brand-google (icon-xref :brand-google))

(def loader-pencil
  (mf/html
   [:svg
    {:viewBox "0 0 677.34762 182.15429"
     :height "182"
     :width "667"
     :id "loader-pencil"}
    [:g
     [:path
      {:id "body-body"
       :d
       "M128.273 0l-3.9 2.77L0 91.078l128.273 91.076 549.075-.006V.008L128.273 0zm20.852 30l498.223.006V152.15l-498.223.007V30zm-25 9.74v102.678l-49.033-34.813-.578-32.64 49.61-35.225z"}]
     [:path
      {:id "loader-line"
       :d
       "M134.482 157.147v25l518.57.008.002-25-518.572-.008z"}]]]))

(mf/defc debug-icons-preview
  {::mf/wrap-props false}
  []
  [:section.debug-icons-preview
   (for [[key val] (sort-by first (ns-publics 'app.main.ui.icons))]
     (when (not= key 'debug-icons-preview)
       [:div.icon-item {:key key}
        (deref val)
        [:span (pr-str key)]]))])
