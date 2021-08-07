(ns discord.config
  "This namespace manages the configuration of the bot, as well as functions for reading and managing
   those configurations."
  (:require
    [clojure.tools.cli :refer [parse-opts]]
    [clojure.data.json :as json]
    [clojure.string :as s]
    [clojure.java.io :as io]
    [integrant.core :as ig]
    [taoensso.timbre :as timbre])
  (:import [java.io IOException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Reading, writing, and managing configuration files
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn load-json-file
  [filename & {:keys [default]}]
  (try
    (json/read-str (slurp filename) :key-fn keyword)
    (catch IOException ioe
      (or default []))))

(defn write-json-file
  [filename data]
  (spit filename (json/write-str data)))

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

(defn ensure-file-exists [filename]
  (when-not (file-exists? filename)
    (create-file filename)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining the command line options
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn usage
  [options-summary]
  (->> ["Discord.clj Bot Library"
        ""
        "Options:"
        options-summary]
       (s/join \newline)))

(defn print-and-exit
  [message exit-status]
  (println message)
  (System/exit exit-status))

;;; Default values and validation constants
(def default-bot-tokens-file-location "data/settings/settings.json")
(def valid-log-levels #{:trace :debug :info :warn :error :fatal :report})
(def builtin-extensions-folder "src/discord/extensions/builtin")

(def command-line-options
  "Available command line options for the bot."
  [["-f" "--tokens-filename" "The location for the file that has the bot token and application ID."
    :default default-bot-tokens-file-location]
   ["-p" "--prefix PREFIX" "The prefix for the bot."
    :default "!"]
   [nil "--[no-]include-builtin-extensions" "Load the builtin extensions."
    :default true]
   ["-l" "--logging-level LEVEL" "The level of debugging to enable"
    :default :info
    :parse-fn #(keyword %)
    :validate [#(contains? valid-log-levels %)
               (->> valid-log-levels
                    (map name)
                    (s/join ", ")
                    (str "Valid log levels: ")) ]]
   ["-e" "--extension-folder PATH" "A path to a folder of extensions to load."
    :default []
    :multi true
    :id :extension-folders
    :update-fn conj]
   ["-h" "--help"]])

(defn parse-command-line-args []
  (let [{:keys [options arguments errors summary]
         :as parsed-options} (parse-opts *command-line-args* command-line-options)]
    (cond
      (:help options) (print-and-exit (usage summary) 0)
      (not-empty errors) (print-and-exit (s/join \newline errors) 1)
      :default options)))

(defmethod ig/init-key :discord/config
  [& _]
  (let [options (parse-command-line-args)
        extension-folders (cond-> (:extension-folders options)
                            (:include-builtin-extensions options) (conj builtin-extensions-folder))
        tokens (load-json-file (:tokens-filename options))]
    (timbre/set-level! (:logging-level options))
    (-> {:extension-folders extension-folders :prefix (:prefix options)}
        (merge tokens))))
