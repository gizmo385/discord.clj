(ns discord.extensions.reactions
  (:require
    [clojure.core.async :refer [go]]
    [clojure.string :as s]
    [discord.types.messages :as msgs]
    [discord.gateway :as gw]
    [taoensso.timbre :as timbre]))

(defmulti handle-message-reaction-by-name
  (fn [reaction auth] (-> reaction (get-in [:emoji :name]) s/lower-case keyword)))

;;; We don't actually need to do anything here
(defmethod handle-message-reaction-by-name :default
  [reaction _]
  (timbre/debugf "Ignoring message reaction: %s" reaction))


(defmethod gw/handle-gateway-event :MESSAGE_REACTION_ADD
  [message auth metadata]
  (let [reaction (msgs/build-message-reaction (:d message))]
    (go (handle-message-reaction-by-name reaction auth))))
