(ns starboard.core
  (:require
    [discord.api.channels :as channels-api]
    [discord.types.channel :as channel]
    [discord.embeds :as e]
    [discord.extensions.reactions :as react]
    [discord.interactions.core :as i]
    [discord.interactions.commands :as cmds]
    [taoensso.timbre :as timbre]))


(def star-reaction-name :⭐)
(def url-encoded-star-reaction "%E2%AD%90")
(def starred-message-threshold 1)

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
        (e/+thumbnail :url (user-avatar-url author-id avatar-hash))
        (e/+field "Message" (format "[Jump to](%s)"
                                    (message->link channel original-message)))
        (e/+field "Channel" (format "<#%s>" channel-id) :inline true)
        (e/+field "Author" (format "<@%s>" author-id) :inline true))))

(defn guild-id->starboard-channel-id
  [guild-id]
  "936180427754963005")

(defn add-message-to-starboard!
  [auth message-channel-id starred-message-id]
  (let [original-message (channels-api/get-channel-message auth message-channel-id starred-message-id)
        source-channel (channels-api/get-channel auth message-channel-id)
        starboard-embed (message->starboard-embed source-channel original-message)
        starboard-channel-id (-> source-channel :guild-id guild-id->starboard-channel-id)]
    (channels-api/send-message-to-channel
      auth message-channel-id
      {:content (format "This message has been added to the starboard (<#%s>)" starboard-channel-id)
       :message_reference {:message_id starred-message-id}
       :allowed_mentions {:parse []}})
    (channels-api/send-message-to-channel auth starboard-channel-id {:embeds [starboard-embed]})))

(defmethod react/handle-message-reaction-by-name star-reaction-name
  [{:keys [channel-id message-id] :as reaction} auth]
  (let [all-star-reactions (channels-api/get-channel-message-reactions
                             auth channel-id message-id url-encoded-star-reaction)]
    (when (>= (count all-star-reactions))
      (add-message-to-starboard! auth channel-id message-id))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Slash commands for interacting with the starboard
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(cmds/register-globally-on-startup!
  (cmds/slash-command
    :starboard "Interact with the starboard"
    (cmds/sub-command :top "Get the top messages on the starboard in this server.")
    (cmds/sub-command-group
      :configure "Configure starboard"
      (cmds/sub-command
        :set-channel "Set the channel that starred messages will be recorded in."
        (cmds/channel-option :channel "The channel to record messages in" true
                             {:channel-types (channel/keys->channel-type-ids [:guild-text])}))
      (cmds/sub-command
        :required-stars "Set the channel that starred messages will be recorded in."
        (cmds/integer-option :amount "Required star count" true {:min-value 1})))))


(defmethod cmds/handle-slash-command-interaction [:starboard :configure :set-channel]
  [{:keys [interaction arguments]} auth _]
  (timbre/infof "Arguments: %s" arguments))
