(ns discord.core (:require [clojure.tools.logging :as log]
            [discord.config :as config]
            [discord.bot :refer [say delete] :as bot]
            [discord.types :as types]
            [discord.client :as client]
            [discord.gateway :as gw])
  (:import [discord.types ConfigurationAuth])
  (:gen-class))

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
              (say "HELLO EVERYONE"))))
