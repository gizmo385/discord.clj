(ns starboard.core
  (:require
    [discord.api.channels :as channels]
    [discord.embeds :as e]
    [discord.extensions.reactions :as react]
    [taoensso.timbre :as timbre]))


(def star-reaction-name :⭐)
(def url-encoded-star-reaction "%E2%AD%90")
(def starred-message-threshold 1)

(def starboard-channel-id "936180427754963005")

(defn user-guild-avatar-url
  [guild-id user-id avatar-hash]
  (format "https://cdn.discordapp.com/guilds/%s/users/%s/avatars/%s.png"
          guild-id user-id avatar-hash))

(defn user-avatar-url
  [user-id avatar-hash]
  (format "https://cdn.discordapp.com/avatars/%s/%s.png" user-id avatar-hash))

(defn message->link
  [channel message]
  (format "https://discord.com/channels/%s/%s/%s"
          (:guild_id channel) (:id channel) (:id message)))

(defn message->starboard-embed
  [channel original-message]
  (let [author-id (get-in original-message [:author :id])
        avatar-hash (get-in original-message [:author :avatar])
        channel-id (:id channel)
        guild-id (:guild-id channel)
        avatar-url (user-avatar-url author-id avatar-hash)]
    (-> (e/create-embed)
        (e/+author :url avatar-url :icon_url (user-avatar-url author-id avatar-hash))
        (e/+field "Message" (format "[Jump to](%s)"
                                    (message->link channel original-message)))
        (e/+field "Channel" (format "<#%s>" channel-id) :inline true)
        (e/+field "Author" (format "<@%s>" author-id) :inline true)
        #_(e/+image :url (user-avatar-url author-id avatar-hash) :height 64 :width 64)))
  )

(defn replicate-starred-message
  [auth channel-id message-id]
  (let [original-message (channels/get-channel-message auth channel-id message-id)
        channel (channels/get-channel auth channel-id)
        message (format "Would add stared message: `%s`" message-id)
        starboard-embed (message->starboard-embed channel original-message)]
    (channels/send-message-to-channel
      auth channel-id {:content "This message has been added to the starboard"
                       :message_reference {:message_id message-id}
                       :allowed_mentions {:parse []}})
    (channels/send-message-to-channel auth starboard-channel-id {:embeds [starboard-embed]})))

(defmethod react/handle-message-reaction-by-name star-reaction-name
  [{:keys [channel-id message-id] :as reaction} auth]
  (let [all-star-reactions (channels/get-channel-message-reactions
                             auth channel-id message-id url-encoded-star-reaction)]
    (when (>= (count all-star-reactions))
      (replicate-starred-message auth channel-id message-id))))
