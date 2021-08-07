(ns discord.core
  "This is the primary entry point for the bot. It starts the entire system."
  (:require
    ;; We import this to ensure the ig/init defmethod is evaluated.
    [discord.bot :as bot]
    [integrant.core :as ig])
  (:gen-class))

(def integrant-config-file "resources/integrant-config.edn")

(defn -main
  [& args]
  (-> integrant-config-file slurp ig/read-string ig/init))
