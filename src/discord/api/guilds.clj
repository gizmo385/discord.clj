(ns discord.api.guilds
  (:require
    [discord.api.base :as api]
    [discord.types.guild :as guild]
    [discord.types.role :as role]))

(defn get-guild
  [auth guild-id]
  (guild/build-guild (api/discord-request auth (format "/guilds/%s" guild-id) :get)))

(defn get-guild-members
  [auth guild-id]
  (let [endpoint (format "/guilds/%s/members" guild-id)]
    (map guild/build-guild-member (api/discord-request auth endpoint :get))))

(defn get-guild-member
  [auth guild-id user-id]
  (let [endpoint (format "/guilds/%s/members/%s" guild-id user-id)]
    (guild/build-guild-member (api/discord-request auth endpoint :get))))

(defn get-guild-roles
  [auth guild-id]
  (let [endpoint (format "/guilds/%s/roles" guild-id)
        guild-roles-response (api/discord-request auth endpoint :get)]
    (map role/build-role guild-roles-response)))
