(ns discord.core
  (:require [discord.bot :as bot])
  (:gen-class))


(defn -main
  "Starts a Discord bot."
  [& args]
  (bot/start-bot!))
