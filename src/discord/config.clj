(ns discord.config
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defonce global-bot-settings "data/settings/settings.json")

;;; Saving, loading, and checking config files
(defmulti file-io
  "Manages the saving and loading of file-io files"
  (fn [filename operation & {:keys [data]}]
    operation))

(defmethod file-io :save [filename _ & {:keys [data]}]
  (spit filename (json/write-str data)))

(defmethod file-io :load [filename _]
  (json/read-str (slurp filename) :key-fn keyword))

(defmethod file-io :check [filename _]
  (.exists (io/as-file filename)))


(defn get-prefix []
  (:prefix (file-io global-bot-settings :load)))

(defn get-bot-name []
  (:bot-name (file-io global-bot-settings :load)))

(defn get-cog-folders []
  (:cog-folders (file-io global-bot-settings :load)))

(defn get-token []
  (:token (file-io global-bot-settings :load)))
