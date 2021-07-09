(ns discord.components
  (:require [discord.interactions :as i]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Enums defined in the Discord developer docs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def action-row-component-type 1)
(def button-component-type 2)
(def select-menu-component-type 3)
(def component-types [action-row-component-type button-component-type select-menu-component-type])

(def primary-button-style 1)
(def secondary-button-style 2)
(def success-button-style 3)
(def danger-button-style 4)
(def link-button-style 5)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Top-level components and action rows
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn action-row
  "Defines a new row of message components."
  [& subcomponents]
  {:type action-row-component-type :components subcomponents})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Button components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn link-button
  "Defines a new button message component WITH a URL."
  [url & {:keys [label] :or {label ""}}]
  (cond-> {:type button-component-type
           :style link-button-style
           :url url}
    (some? label) (assoc :label label)))

(defn- non-link-button
  "Defines a new button message component WITHOUT a URL."
  [style custom-id label]
  (cond-> {:type button-component-type
           :style style
           :custom_id custom-id}
    (some? label) (assoc :label label)))

(defn primary-button
  "Defines a new button message using the PRIMARY button style."
  [custom-id & {:keys [label]}]
  (non-link-button primary-button-style custom-id label))

(defn secondary-button
  "Defines a new button message using the SECONDARY button style."
  [custom-id & {:keys [label]}]
  (non-link-button secondary-button-style custom-id label))

(defn success-button
  "Defines a new button message using the SUCCESS button style."
  [custom-id & {:keys [label]}]
  (non-link-button success-button-style custom-id label))

(defn danger-button
  "Defines a new button message using the DANGER button style."
  [custom-id & {:keys [label]}]
  (non-link-button danger-button-style custom-id label))

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
  (cond-> {:type select-menu-component-type :custom_id custom-id :options options}
    (some? placeholder-text) (assoc :placeholder placeholder-text)
    (some? select-min) (assoc :min_values select-min)
    (some? select-max) (assoc :max_values select-max)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handling message component interactions
;;;
;;; If a user intends to implement custom interactions, they should define methods for either
;;; handle-menu-selection or handle-button press, depending on the kind of interaction they're
;;; creating.
;;;
;;; The other multi-methods defined below are just for traversing through the abstraction
;;; indirection and determing.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti handle-menu-selection
  (fn [original-message gateway custom-id selected-values]
    (keyword custom-id)))

(defmulti handle-button-press
  (fn [original-message gateway custom-id]
    (keyword custom-id)))

;; Here, we dispatch between interaction multimethods for different kinds of message components.
;; Different component interactions supply different information, so we want to make that explicit
;; to users of the library.
(defmulti handle-message-component-interaction
  (fn [original-message gateway data]
    (:component_type data)))

(defmethod handle-message-component-interaction button-component-type
  [original-message gateway payload]
  (handle-button-press original-message gateway (:custom_id payload)))

(defmethod handle-message-component-interaction select-menu-component-type
  [original-message gateway payload]
  (handle-menu-selection
    original-message gateway (:custom_id payload) (:values payload)))

;; When an interaction with a component has been received, we'll pass it onto handling which will
;; determine what kind of message component the interaction originated from
(defmethod i/handle-interaction i/message-component-interaction
  [discord-message gateway]
  (handle-message-component-interaction discord-message gateway (:data discord-message)))
