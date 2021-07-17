(ns discord.api.guilds
  (:require
    [discord.api.base :as api]
    [discord.types.guild :as guild]))

(defn get-guild
  [auth guild-id]
  (guild/build-guild (api/discord-request auth (format "/guilds/%s" (:id guild-id)) :get)))

(defn get-guild-members
  [auth guild-id]
  (let  [endpoint (format "/guilds/%s/members" (:id guild-id))]
    (map guild/build-guild-member (api/discord-request auth endpoint :get))))

(defn get-guild-member
  [auth guild-id user-id]
  (let [endpoint (format "/guilds/%s/members/%s" (:id guild-id) (:id user-id))]
    (guild/build-guild-member (api/discord-request auth endpoint :get))))
