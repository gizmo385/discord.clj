(ns discord.core
  (:require [clojure.tools.logging :as log]
            [discord.config :as config]
            [discord.types :as types]
            [discord.bot :as bot]
            [discord.gateway :as gw])
  (:import [discord.types ConfigurationAuth])
  (:gen-class))


(defmulti message-handler
  (fn [bot message] message))

(defmethod message-handler :default
  [bot message]
  (log/info message))

(defn -main
  "Spins up a new bot and reads messages from it"
  [& args]
  (with-open [discord-bot (bot/create-discord-bot message-handler)]
    (while true (Thread/sleep 3000))))
