(ns discord.core
  (:require
    [discord.config :as config]
    [discord.bot :as bot]
    [integrant.core :as ig])
  (:gen-class))


(defn -main
  "Starts a Discord bot."
  [& args]
  (bot/start-bot!))
