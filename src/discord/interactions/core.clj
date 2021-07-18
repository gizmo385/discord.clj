(ns discord.interactions.core
  (:require
    [clojure.data.json :as json]
    [discord.gateway :as gw]
    [discord.api.interactions :as interactions-api]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining some constants from the developer documentation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def slash-command-interaction 2)
(def message-component-interaction 3)

(def channel-update-with-source-response 4)
(def deferred-channel-message-with-source-response 5)
(def deferred-update-message-response 6)
(def update-message-response 7)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Responding to interactions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn respond*
  "Responds to an interaction with a message of a certain type, optionally including message
   content, message components, and message embeds."
  [interaction auth response-type message-content components embed]
  (let [response (cond-> {:type response-type}
                   (some? message-content) (assoc-in [:data :content] message-content)
                   (some? components) (assoc-in [:data :components] components)
                   (some? embed) (assoc-in [:data :embeds] embed))]
    (interactions-api/respond-to-interaction auth interaction response)))

(defn channel-message-response
  [interaction auth message components embed]
  (respond* interaction auth channel-update-with-source-response message components embed))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handling generic interactions over the gateway
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti handle-interaction
  (fn [discord-message auth metadata]
    (:type discord-message)))

(defmethod gw/handle-gateway-event :INTERACTION_CREATE
  [message auth metadata]
  (handle-interaction (:d message) auth metadata))
