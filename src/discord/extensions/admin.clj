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
  [client message action]
  (doseq [user (:user-mentions message)]
    (let [user-id (:id user)
          guild-id (get-in message [:channel :guild-id])]
      (action client guild-id user-id))))

(bot/install-modules!
  (bot/with-module :admin
    (bot/with-module :player
      (bot/command :kick [client message & _] (doto-mentioned-users client message http/kick))
      (bot/command :ban [client message & _] (doto-mentioned-users client message http/ban))
      (bot/command :unban [client message & _] (doto-mentioned-users client message http/unban)))

    (bot/with-module :region
      (bot/command
        :get [client message]
        (let [guild-id      (get-in message [:channel :guild-id])
              guild         (http/get-guild client guild-id)
              voice-region  (-> guild :region name)]
          (bot/reply-in-channel
            client message (format "The guild's voice region is currently \"%s\"" voice-region))))
      (bot/command
        :list [client message]
        (let [reply (format "Supported voice regions: %s" (s/join ", " (keys types/server-region)))]
          (bot/reply-in-channel client message reply)))
      (bot/command
        :move [client message new-region]
        (let [guild-id (get-in message [:channel :guild-id])]
          (if-let [region-keyword (get types/server-region new-region)]
            (do
              (bot/reply-in-channel
                client message (format "Moving voice server to \"%s\"" new-region))
              (http/modify-server client guild-id :region new-region))
            (bot/reply-in-channel client message (format "The region \"%s\" does not exist."))))))

    (bot/command
      :shutdown [client message]
      (.close client)
      (System/exit 0))
    (bot/command
      :broadcast [client message message-to-broadcast]
      (let [bcast-message (->> message :content utils/words rest (s/join " "))
            servers (http/get-servers client)]
        (doseq [server servers]
          (http/send-message client server bcast-message))))))
