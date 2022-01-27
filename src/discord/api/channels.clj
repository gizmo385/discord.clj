(ns discord.api.channels
  (:require
    [discord.api.base :as api]
    [discord.types.messages :as messages]
    [discord.types.channel :as channel]))

(defn send-message-to-channel
  "Given a channel snowflake and a message, sends a message to that channel via the Discord API."
  [auth channel-id content]
  (if (some? channel-id)
    (let [endpoint (format "/channels/%s/messages" channel-id)]
      (api/discord-request auth endpoint :post :json content))))

(defn create-dm-channel
  "Create a DM channel with a particular user."
  [auth user]
  (let [endpoint (format "/users/@me/channels")
        body {:recipient_id (:id user)}]
    (api/discord-request auth endpoint :post :json body)))

(defn delete-message
  "Remove a message by ID."
  [auth message]
  (let [endpoint (format "/channels/%s/messages/%s" (:channel-id message) (:id message))]
    (api/discord-request auth endpoint :delete)))

(defn create-reaction
  [auth channel-id message-id emoji]
  (let [endpoint (format "/channels/%s/messages/%s/reactions/%s:%s/@me"
                         channel-id message-id (:name emoji) (:id emoji))]
    (api/discord-request auth endpoint :put)))

(defn get-channel-message-reactions
  [auth channel-id message-id emoji]
  (let [endpoint (format "/channels/%s/messages/%s/reactions/%s" channel-id message-id emoji)]
    (api/discord-request auth endpoint :get)))

(defn get-channel-message
  [auth channel-id message-id]
  (let [endpoint (format "/channels/%s/messages/%s" channel-id message-id)]
    (messages/build-message (api/discord-request auth endpoint :get))))

(defn get-channel
  [auth channel-id]
  (let [endpoint (format "/channels/%s" channel-id)]
    (api/discord-request auth endpoint :get)))
