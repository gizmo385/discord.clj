(ns discord.interactions.core
  "This namespace implements the core routing and response functionality for handling interactions
   within Discord. Functions and implementations for handling specific kinds of interactions are
   delegated to other namespaces, such as `discord.interactions.commands` for various kinds of
   application commands (eg: slash commands)."
  (:require
    [clojure.data.json :as json]
    [discord.gateway :as gw]
    [discord.api.interactions :as interactions-api]
    [discord.types.interaction :as interaction]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining some constants from the developer documentation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def command-interaction 2)
(def message-component-interaction 3)

(def channel-update-with-source-response 4)
(def deferred-channel-message-with-source-response 5)
(def deferred-update-message-response 6)
(def update-message-response 7)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Responding to interactions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn respond*
  "Helper function for responds to an interaction with a message of a certain type, optionally
   including message content, message components, and message embeds. This function is partially
   applied to more clear interaction response functions below."
  ([response-type interaction auth message-content]
   (respond* response-type interaction auth message-content nil nil))
  ([response-type interaction auth message-content components]
   (respond* response-type interaction auth message-content components nil))
  ([response-type interaction auth message-content components embeds]
   (let [response (cond-> {:type response-type}
                    (some? message-content) (assoc-in [:data :content] message-content)
                    (some? components) (assoc-in [:data :components] components)
                    (some? embeds) (assoc-in [:data :embeds] embeds))]
     (interactions-api/respond-to-interaction auth interaction response))))

(def channel-message-response
  "Responds to an interaction with a message in the channel the interaction originated in."
  (partial respond* channel-update-with-source-response))

(def deferred-channel-message
  "Acknowledges an interaction and allows for the bot to edit the response later. The user will see
   a loading state as the initial response."
  (partial respond* deferred-channel-message-with-source-response))

(def deferred-update-message
  "Acknowledges a *component* interaction and allows the bot to edit the response later. The user
   will *NOT* see a loading state. This response type is only valid for component-based interactions
   and is not applicable to slash-command responses."
  (partial respond* deferred-update-message-response))

(def update-message
  "Updates the message that a component was attached to. This response type is only valid for
   component-based interactions and is not applicable to slash-command responses."
  (partial respond* update-message-response))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Other interaction-generic helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn interaction->user-id
  "Returns the user ID of the user who invoked the interaction."
  [interaction]
  (or (get-in interaction [:member :user :id]) ; For guild messages
      (get-in interaction [:user :id]))) ; For DMs

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handling generic interactions over the gateway
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti handle-interaction
  "This multi-method handles interaction responses that come over the Discord gateway. At the time of
   writing, interactions are limited to Slash Commands and Message Components (buttons, select menus,
   etc), but additional interactions could be added later."
  (fn [discord-message auth metadata]
    (:type discord-message)))

(defmethod gw/handle-gateway-event :INTERACTION_CREATE
  [message auth metadata]
  (-> message :d interaction/build-interaction (handle-interaction auth metadata)))
