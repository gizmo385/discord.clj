(ns discord.cogs.admin
  (:require [clojure.string :as s]
            [clojure.pprint :refer [pprint]]
            [discord.bot :as bot]
            [discord.utils :as utils]
            [discord.http :as http]))


(bot/defcog admin [client message]
  "Commands for common server adminstrative tasks."
  (:kick
    "Kicks the mentioned users from the server."
    (doseq [user (:user-mentions message)]
      (let [user-id (:id user)
            guild-id (get-in message [:channel :guild-id])]
        (http/kick client guild-id user-id))))

  (:ban
    "Bans the mentioned users from the server."
    (doseq [user (:user-mentions message)]
      (let [guild-id (get-in message [:channel :guild-id])]
        (http/ban client guild-id user))))

  (:unban
    "Unbans the mentioned users from the server."
    (doseq [user (:user-mentions message)]
      (let [guild-id (get-in message [:channel :guild-id])]
        (http/unban client guild-id user))))

  (:broadcast
    "Sends a message to all servers to which the bot is connected."
    (let [bcast-message (->> message :content utils/words rest (s/join " "))
          servers (http/get-servers client)]
      (doseq [server servers]
        (http/send-message client server bcast-message)))))
