(ns discord.core
  (:require [clojure.tools.logging :as log]
            [clojure.string :as s]
            [discord.bot :refer [say delete] :as bot]
            [discord.utils :as utils]
            [discord.http :as http])
  (:gen-class))

;;; Example Cog implementation for admin commands
(bot/defcog admin-cog)

(defmethod admin-cog "kick"
  [client message]
  (doseq [user  (:user-mentions message)]
    (let [user-id (:id user)
          guild-id (get-in message [:channel :guild-id] message)]
      (http/kick client guild-id user-id))))

(defmethod admin-cog "broadcast"
  [client message]
  (let [message-to-broadcast (->> message :content utils/words rest (s/join " "))
        servers (http/get-servers client)]
    (doseq [server servers]
      (http/send-message client (:id server) message-to-broadcast))))

;;; Other cog
(bot/defcog other-cog)

(defn -main
  "Spins up a new client and reads messages from it"
  [& args]
  (bot/open-with-cogs
    "TestDiscordBot" "^"
    :say    (fn [client message]
              (say (:content message)))
    :botsay (fn [client message]
              (say (:content message))
              (delete message))
    :greet  (fn [_ _]
              (say "HELLO EVERYONE"))
    :admin  admin-cog))
