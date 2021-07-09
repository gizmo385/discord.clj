(ns discord.components)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Enums defined in the Discord developer docs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def COMPONENT-TYPES
  {:action-row  1
   :button      2
   :select-menu 3})

(def BUTTON-STYLES
  {:primary   1
   :secondary 2
   :success   3
   :danger    4
   :link      5})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Top-level components and action rows
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def EMPTY-ACTION-ROW
  {:type      (:action-row COMPONENT-TYPES)
   :components []})

(defn action-row
  "Defines a new row of message components."
  [& subcomponents]
  (assoc EMPTY-ACTION-ROW :components subcomponents))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Button components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn link-button
  "Defines a new button message component WITH a URL."
  [url & {:keys [label] :or {label ""}}]
  (cond-> {:type (:button COMPONENT-TYPES)
           :style (:link BUTTON-STYLES)
           :url url}
    (some? label) (assoc :label label)))

(defn- non-link-button
  "Defines a new button message component WITHOUT a URL."
  [style custom-id label]
  (cond-> {:type (:button COMPONENT-TYPES)
           :style style
           :custom_id custom-id}
    (some? label) (assoc :label label)))

(defn primary-button
  "Defines a new button message using the PRIMARY button style."
  [custom-id & {:keys [label]}]
  (non-link-button (:primary BUTTON-STYLES) custom-id label))

(defn secondary-button
  "Defines a new button message using the SECONDARY button style."
  [custom-id & {:keys [label]}]
  (non-link-button (:secondary BUTTON-STYLES) custom-id label))

(defn success-button
  "Defines a new button message using the SUCCESS button style."
  [custom-id & {:keys [label]}]
  (non-link-button (:success BUTTON-STYLES) custom-id label))

(defn danger-button
  "Defines a new button message using the DANGER button style."
  [custom-id & {:keys [label]}]
  (non-link-button (:danger BUTTON-STYLES) custom-id label))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Select components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn menu-option
  [label value & {:keys [description default?]}]
  (cond-> {:label label :value value}
    (some? description) (assoc :description description)
    (some? default?) (assoc :default default?)))

(defn select-menu
  [custom-id options & {:keys [placeholder-text select-min select-max]}]
  (cond-> {:type (:select-menu COMPONENT-TYPES) :custom_id custom-id :options options}
    (some? placeholder-text) (assoc :placeholder placeholder-text)
    (some? select-min) (assoc :min_values select-min)
    (some? select-max) (assoc :max_values select-max)))
