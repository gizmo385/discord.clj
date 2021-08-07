(ns discord.config
  "This namespace manages the configuration of the bot, as well as functions for reading and managing
   those configurations."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as s]
    [clojure.tools.cli :refer [parse-opts]]
    [integrant.core :as ig]
    [taoensso.timbre :as timbre])
  (:import [java.io IOException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Reading, writing, and managing configuration files
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn load-bot-config
  [filename]
  (try
    (edn/read-string (slurp filename))
    (catch IOException ioe
      (throw (ex-info "Error loading bot config!" {:config-file filename})))))

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
(def default-bot-settings-file-location "resources/bot-config.edn")
(def valid-log-levels #{:trace :debug :info :warn :error :fatal :report})
(def builtin-extensions-folder "src/discord/extensions/builtin")

(def command-line-options
  "Available command line options for the bot."
  [["-f" "--settings-filename" "The location for the file that has the core bot settings."
    :default default-bot-settings-file-location]
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
        settings (load-bot-config (:settings-filename options))]
    (timbre/set-level! (:logging-level options))
    (-> {:extension-folders extension-folders :prefix (:prefix options)}
        (merge settings))))
