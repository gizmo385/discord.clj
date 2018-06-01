(ns discord.extensions.admin
  (:require [clojure.string :as s]
            [clojure.pprint :refer [pprint]]
            [taoensso.timbre :as timbre]
            [discord.bot :as bot]
            [discord.permissions :as perm]
            [discord.utils :as utils]
            [discord.http :as http]
            [discord.types :as types]))

(bot/defextension admin [client message]
  "Commands for common server adminstrative tasks."
  (:kick
    {:requires [perm/KICK-MEMBERS]}
    "Kicks the mentioned users from the server."
    (doseq [user (:user-mentions message)]
      (let [user-id (:id user)
            guild-id (get-in message [:channel :guild-id])]
        (http/kick client guild-id user-id))))

  (:ban
    {:requires [perm/BAN-MEMBERS]}
    "Bans the mentioned users from the server."
    (doseq [user (:user-mentions message)]
      (let [guild-id (get-in message [:channel :guild-id])]
        (http/ban client guild-id user))))

  (:unban
    {:requires [perm/BAN-MEMBERS]}
    "Unbans the mentioned users from the server."
    (doseq [user (:user-mentions message)]
      (let [guild-id (get-in message [:channel :guild-id])]
        (http/unban client guild-id user))))

  (:broadcast
    {:requires [perm/ADMINISTRATOR]}
    "Sends a message to all servers to which the bot is connected."
    (let [bcast-message (->> message :content utils/words rest (s/join " "))
          servers (http/get-servers client)]
      (doseq [server servers]
        (http/send-message client server bcast-message))))

  (:voiceregion
    "Returns the current voice region for the guild."
    {:requires [perm/MANAGE-GUILD]}
    (let [guild-id      (get-in message [:channel :guild-id])
          guild         (http/get-guild client guild-id)
          voice-region  (-> guild :region name)]
      (bot/say (format "The guild's voice region is currently \"%s\"" voice-region))))

  (:regionlist
    {:requires [perm/MANAGE-GUILD]}
    "Lists all supported voice regions.")

  (:regionmove
    {:requires [perm/MANAGE-GUILD]}
    "Moves the voice region for the guild to a new location."
    (let [desired-region (->> message :content utils/words rest (s/join " "))
          guild-id (get-in message [:channel :guild-id])]
      (if-let [region-keyword (get types/server-region desired-region)]
        (do
          (bot/say (format "Moving voice server to \"%s\"" desired-region))
          (http/modify-server client guild-id :region desired-region))
        (bot/say (format "The region \"%s\" does not exist."))))))
