(ns discord.core
  (:require [clojure.tools.logging :as log]
            [discord.config :as config]
            [discord.bot :refer [say] :as bot]
            [discord.types :as types]
            [discord.client :as client]
            [discord.gateway :as gw])
  (:import [discord.types ConfigurationAuth])
  (:gen-class))

(defn say-cog
  [client message]
  (say (:content message)))

(defn -main
  "Spins up a new client and reads messages from it"
  [& args]
  (let [say-cog (bot/create-extension "say" say-cog)]
    (with-open [discord-bot (bot/create-bot "TestBot" [say-cog] "^")]
      (while true (Thread/sleep 3000)))))
