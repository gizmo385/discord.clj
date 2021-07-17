(ns discord.extensions.utils
  (:require
    [clojure.core.async :refer [go >!] :as async]
    [discord.api.channels :as c]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Functions to quickly delete or reply to messages from users of the bot
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn build-reply
  [channel reply components embed]
  (let [content (cond-> {:content reply}
                  (some? components) (assoc :components components)
                  (some? embed) (assoc :embeds embed))]
    {:channel channel :content content}))

(defn reply-in-channel
  "Sends a message in response to a message from a user in the channel the message originated in.

   Optionally, message components or embeds can be included."
  ([gateway original-message reply]
   (reply-in-channel gateway original-message reply nil nil))
  ([gateway original-message reply components]
   (reply-in-channel gateway original-message reply components nil))
  ([gateway original-message reply components embed]
   (let [send-channel (get-in gateway [:metadata :send-chan])
         message-channel (:channel-id original-message)]
     (go (>! send-channel (build-reply message-channel reply components embed))))))

(defn reply-in-dm
  "Sends a reply back to the message sender in a DM channel.

   Optionally, message components and embeds can be included."
  ([gateway original-message reply]
   (reply-in-dm gateway original-message reply nil nil))
  ([gateway original-message reply components]
   (reply-in-dm gateway original-message reply components nil))
  ([gateway original-message reply components embed]
   (let [dm-channel (c/create-dm-channel gateway (:author original-message))
         send-channel (get-in gateway [:metadata :send-chan])]
     (go (>! send-channel (build-reply dm-channel reply components embed))))))

(defn delete-original-message
  [client original-message]
  (c/delete-message client (:channel-id original-message) original-message))
