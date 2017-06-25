(ns discord.config
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defonce global-bot-settings "data/settings/settings.json")

;;; Saving, loading, and checking config files
(defmulti config
  "Manages the saving and loading of config files"
  (fn [filename operation & {:keys [data]}]
    operation))

(defmethod config :save [filename _ & {:keys [data]}]
  (spit filename (json/write-str data)))

(defmethod config :load [filename _]
  (json/read-str (slurp filename) :key-fn keyword))

(defmethod config :check [filename _]
  (.exists (io/as-file filename)))

(defn get-token []
  (:token (config global-bot-settings :load)))
