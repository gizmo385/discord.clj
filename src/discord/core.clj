(ns discord.core
  (:require
    ;; We import this to ensure the ig/init defmethod is evaluated.
    [discord.bot :as bot]
    [integrant.core :as ig])
  (:gen-class))

(defn -main
  [& args]
  (-> "resources/config.edn" slurp ig/read-string ig/init))
