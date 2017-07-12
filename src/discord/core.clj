(ns discord.core
  (:require [clojure.tools.logging :as log]
            [clojure.string :as s]
            [discord.bot :refer [say delete] :as bot]
            [discord.config :as config]
            [discord.utils :as utils]
            [discord.http :as http])
  (:gen-class))

;;; Example Cog implementation for admin commands
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

(defn -main
  "Creates a new discord bot and supplies a series of extensions to it."
  [& args]
  (let [bot-name    (config/get-bot-name)
        prefix      (config/get-prefix)
        cog-folders (config/get-cog-folders)]
    (bot/with-file-cogs bot-name prefix cog-folders)))
