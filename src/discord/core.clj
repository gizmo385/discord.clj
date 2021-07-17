(ns discord.core
  (:require
    [discord.config :as config]
    [discord.types.auth :as auth]
    [discord.gateway-v2 :as gateway]
    [discord.bot :as bot]
    [discord.bot-v2 :as bot-v2]
    [integrant.core :as ig])
  (:gen-class))


(defn -main
  [& args]
  (ig/init (ig/read-string (slurp "resources/config.edn"))))

#_(defn -main
  "Starts a Discord bot."
  [& args]
  (bot/start-bot!))
