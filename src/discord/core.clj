(ns discord.core
  (:require
    [discord.config :as config]
    [discord.types.auth :as auth]
    [discord.bot :as bot]
    [integrant.core :as ig])
  (:gen-class))


(defn -main
  [& args]
  (ig/init (ig/read-string (slurp "resources/config.edn"))))
