(ns discord.api.misc
  (:require
    [discord.api.base :as api]))

(defn get-bot-gateway
  [auth]
  (api/discord-request auth "/gateway/bot" :get))

(defn get-bot-gateway-url
  [auth]
  (:url (get-bot-gateway auth)))
