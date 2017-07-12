(ns discord.cogs.admin
  (:require [clojure.string :as s]
            [discord.bot :as bot]
            [discord.utils :as utils]
            [discord.http :as http]))

(bot/defcog admin [client message]
  (:kick
    (doseq [user (:user-mentions message)]
      (let [user-id (:id user)
            guild-id (get-in message [:channel :guild-id] message)]
        (http/kick client guild-id user-id))))

  (:broadcast
    (let [bcast-message (->> message :content utils/words rest (s/join " "))
          servers (http/get-servers client)]
      (doseq [server servers]
        (http/send-message client server bcast-message)))))

