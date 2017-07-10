(ns discord.core
  (:require [clojure.tools.logging :as log]
            [clojure.string :as s]
            [discord.bot :refer [say delete] :as bot]
            [discord.utils :as utils]
            [discord.http :as http])
  (:gen-class))

;;; Example Cog implementation for admin commands
(bot/defcog admin-cog [client message]
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


(defn -main
  "Creates a new discord bot and supplies a series of extensions to it."
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
