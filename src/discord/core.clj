(ns discord.core
  (:require [clojure.tools.logging :as log]
            [clojure.string :as s]
            [discord.config :as config]
            [discord.bot :refer [say delete] :as bot]
            [discord.http :as http]
            [discord.gateway :as gw])
  (:import [discord.types ConfigurationAuth])
  (:gen-class))

(defmulti admin-cog
  (fn [client message]
    (-> message :content (s/split #"\s+") (first))))

(defmethod admin-cog "kick"
  [client message]
  (log/info (format "Users: %s" (with-out-str (prn (:user-mentions message)))))
  (log/info (format "Message: %s" (with-out-str (prn message))))
  (doall
    (for [user  (:user-mentions message)
          :let  [user-id (:id user)
                 guild-id (get-in message [:channel :guild-id] message)]]
      (do
        (log/info (format "User ID: %s" user-id))
        (log/info (format "Guild ID: %s" guild-id))
        (log/info (format "Attempting to kick: %s" (with-out-str (prn user))))
        (log/info (http/kick client guild-id user-id))))))

(defmethod admin-cog :default
  [_ message]
  (say (format "Unrecognized command: %s" (:content message))))

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
