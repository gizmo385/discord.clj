(ns discord.extensions.admin
  (:require [clojure.string :as s]
            [clojure.pprint :refer [pprint]]
            [taoensso.timbre :as timbre]
            [discord.bot :as bot]
            [discord.permissions :as perm]
            [discord.utils :as utils]
            [discord.http :as http]
            [discord.types :as types]))

(defn doto-mentioned-users
  [client message action required-permission]
  (if (perm/has-permission? client message required-permission)
    (doseq [user (:user-mentions message)]
      (let [user-id (:id user)
            guild-id (get-in message [:channel :guild-id])]
        (action client guild-id user-id)))
    (bot/reply-in-channel "You don't have the permission to execute that command.")))

(bot/install-prefix-commands!
  (bot/prefix-command-tree :admin
    (bot/prefix-command-tree :user
      (bot/prefix-command :kick [client message & _] (doto-mentioned-users client message http/kick))
      (bot/prefix-command :ban [client message & _] (doto-mentioned-users client message http/ban))
      (bot/prefix-command :unban [client message & _] (doto-mentioned-users client message http/unban)))

    (bot/prefix-command-tree :region
      (bot/prefix-command
        :get [client message]
        (let [guild-id      (get-in message [:channel :guild-id])
              guild         (http/get-guild client guild-id)
              voice-region  (-> guild :region name)]
          (bot/reply-in-channel
            client message (format "The guild's voice region is currently \"%s\"" voice-region))))
      (bot/prefix-command
        :list [client message]
        (let [reply (format "Supported voice regions: %s" (s/join ", " (keys types/server-region)))]
          (bot/reply-in-channel client message reply)))
      (bot/prefix-command
        :move [client message new-region]
        (if (perm/has-permission? client message perm/MANAGE-GUILD)
          (let [guild-id (get-in message [:channel :guild-id])]
            (if-let [region-keyword (get types/server-region new-region)]
              (do
                (bot/reply-in-channel
                  client message (format "Moving voice server to \"%s\"" new-region))
                (http/modify-server client guild-id :region new-region))
              (bot/reply-in-channel client message (format "The region \"%s\" does not exist."))))
          (bot/reply-in-channel
            client message "You don't have permission to move the voice region!"))))

    (bot/prefix-command
      :shutdown [client message]
      (if true #_(perm/has-permission? client message perm/ADMINISTRATOR)
        (do (.close client)
            (System/exit 0))
        (bot/reply-in-channel client message "https://media.giphy.com/media/RX3vhj311HKLe/giphy.gif")))
    ))
