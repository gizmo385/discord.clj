(ns discord.config
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [integrant.core :as ig])
  (:import [java.io IOException]))

(defonce global-bot-settings "data/settings/settings.json")

;;; Saving, loading, and checking config files
(defmulti file-io
  "Manages the saving and loading of file-io files.

   Optional arguments per operation:
    - :save
      - data: The data that you wish to save to the file.
   - :load
      - :default: The value to be returned if data could not be loaded."
  (fn [filename operation & {:keys [data default]}]
    operation))

(defmethod file-io :save [filename _ & {:keys [data]}]
  (spit filename (json/write-str data)))

(defmethod file-io :load [filename _ & {:keys [default]}]
  (try
    (json/read-str (slurp filename) :key-fn keyword)
    (catch IOException ioe
      (or default []))))

(defn file-exists?
  "Returns whether or not the specified filename exists on disk."
  [filename]
  (-> filename
      (io/as-file)
      (.exists)))

(defn create-file
  "Creates the file specified by the filename. Also creates all necessary parent directories."
  [filename]
  (let [f (io/as-file filename)]
    (-> f
        (.getParentFile)
        (.getAbsolutePath)
        (io/file)
        (.mkdirs))
    (.createNewFile f)))

(defmethod file-io :check [filename _]
  (if-not (file-exists? filename)
    (create-file filename)))

(defn get-prefix
  ([] (get-prefix global-bot-settings))
  ([filename] (:prefix (file-io filename :load))))

(defn get-bot-name
  ([] (get-bot-name global-bot-settings))
  ([filename] (:bot-name (file-io filename :load))))

(defn get-extension-folders
  ([] (get-extension-folders global-bot-settings))
  ([filename] (:extension-folders (file-io filename :load))))

(defn get-token
  ([] (get-token global-bot-settings))
  ([filename] (:token (file-io filename :load))))

(defn get-application-id
  ([] (get-application-id global-bot-settings))
  ([filename] (:application-id (file-io filename :load))))

(defmethod ig/init-key :discord/config
  [_ {:keys [filename]}]
  (file-io filename :load))
