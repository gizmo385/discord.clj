(ns discord.extensions.core
  (:require
    [clojure.java.io :as io]
    [clojure.string :as s]
    [discord.extensions.utils :as utils]
    [discord.config :as config]
    [taoensso.timbre :as timbre]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Modules and command resolution.
;;;
;;; Modules are essentially containers that can hold other modules and commands, forming a tree of
;;; commands and modules. Command resolution is a depth-first traversal through that tree, resulting
;;; in a CommandInvocation record.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord CommandInvocation
  [full-command handler args])

(defn command->invocation
  [cmd modules]
  (letfn [(split-command->invocation [[next-command-element & rest-of-command] modules]
            (let [layer (get modules (keyword next-command-element))]
              (cond
                (fn? layer) (->CommandInvocation cmd layer rest-of-command)
                (map? layer) (split-command->invocation rest-of-command layer))))]
    (split-command->invocation (s/split cmd #"\s+") modules)))

(defn execute-invocation
  [invocation gateway message]
  (apply (:handler invocation) gateway message (:args invocation)))

(defn prefix-command-tree [module-name & children]
  {module-name (apply merge children)})

(defmacro prefix-command
  [command & fn-tail]
  `(hash-map ~command (fn ~(symbol (format "command-invocation-%s" (name command))) ~@fn-tail)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Custom Discord message handlers
;;;
;;; This allows the creation of more advanced plugins that directly intercept messages that in a
;;; way that allows for more customized handling than what is available through extensions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce message-handlers (atom []))

(defn add-handler!
  "Registers a new message handler. The handler, handler-fn, should be a function of 3 arguments
   where the first argument is the bot's prefix, the second is the Discord gateway and the third
   is the message in the Discord text channel."
  [handler-fn]
  (swap! message-handlers conj handler-fn))

(defn clear-handlers! []
  (reset! message-handlers []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining the global module/command registry
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce installed-prefix-commands (atom {}))

(defn reset-installed-prefix-commands! []
  (reset! installed-prefix-commands {}))

(defn install-prefix-commands!
  [& new-modules]
  (doseq [nm new-modules]
    (swap! installed-prefix-commands merge nm)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Loading and reloading extension functionality
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-clojure-files
  "Given a directory, returns all '.clj' files in that folder."
  [folder]
  (->> folder
       (io/file)
       (file-seq)
       (filter (fn [f] (s/ends-with? f ".clj")))
       (map (fn [f] (.getAbsolutePath f)))))

(defn load-clojure-files-in-folder!
  "Given a directory, loads all of the .clj files in that directory tree. This can be used to
   dynamically load extensions defined with defextension or manually calling register-extension!
   out of a folder."
  [folder]
  (let [clojure-files (get-clojure-files folder)]
    (doseq [filename clojure-files]
      (timbre/infof "Loading extensions from: %s" filename)
      (try (load-file filename)
           (catch Exception e
             (timbre/error e "Error while loading: %s" filename))))))

(defn load-module-folders! []
  (doseq [folder (config/get-extension-folders)]
    (load-clojure-files-in-folder! folder))
  (let [installed-module-names (keys @installed-prefix-commands)]
    (timbre/infof "Loaded %d extensions: %s."
                  (count installed-module-names)
                  (s/join ", " installed-module-names))))


;; This forward declaration of the register-builtins! function is necessary due to the way that we
;; bootstrap the reload functionality. The reload function needs to _also_ be able to register
;; builtins and the register builtins functionality needs to be able to refer to the
;; reload-all-commands! functionality.
(declare register-builtins!)

(defn reload-all-commands!
  [gateway message]
  "Reload the configured extension folders."
  ;; TODO: Check permissions of the user invoking the command
  (reset-installed-prefix-commands!)
  (clear-handlers!)
  (load-module-folders!)
  (register-builtins!)
  ;(slash/register-global-commands! (types/configuration-auth))
  (utils/reply-in-channel gateway message "Successfully reloaded all extension folders."))

(defn register-builtins! []
  (install-prefix-commands!
    (prefix-command
      :help [gateway original-message]
      (utils/reply-in-dm gateway original-message (str (keys @installed-prefix-commands))))
    (prefix-command
      :reload [gateway message]
      (reload-all-commands! gateway message))))
