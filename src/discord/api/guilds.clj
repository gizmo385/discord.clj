(ns discord.api.guilds
  (:require
    [discord.api.base :as api]
    [discord.types.guild :as guild]
    [discord.types.role :as role]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Retrieving information about guilds
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Modifying guilds
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn change-voice-region
  [auth guild-id new-region]
  (let [endpoint (format "/guilds/%s" guild-id)]
    (api/discord-request auth endpoint :patch :json {:region new-region})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Modifying guilds
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn create-role
  [auth guild-id role-name]
  (let [endpoint (format "/guilds/%s/roles" guild-id)]
    (api/discord-request auth endpoint :post :json {:name role-name :mentionable true})))

(defn give-user-role
  [auth guild-id user-id role-id]
  (let [endpoint (format "/guilds/%s/members/%s/roles/%s" guild-id user-id role-id)]
    (api/discord-request auth endpoint :put)))
