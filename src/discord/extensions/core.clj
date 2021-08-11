(ns discord.extensions.core
  "This namespace implements the core definition, routing, and handling of message content based
   bot functionality, including prefix commands (eg: `!command`) and direct message handling, like
   might be necessary for a bot blocking foul words or handling aliases."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as s]
    [discord.config :as config]
    [discord.extensions.utils :as utils]
    [discord.interactions.commands :as cmds]
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
  "Given a prefix command and the modules (or trees of available commands), builds a command
   invocation, which can be invoked to actually perform the command."
  [cmd modules]
  (letfn [(split-command->invocation [[next-command-element & rest-of-command] modules]
            (let [layer (get modules (keyword next-command-element))]
              (cond
                (fn? layer) (->CommandInvocation cmd layer rest-of-command)
                (map? layer) (split-command->invocation rest-of-command layer))))]
    (split-command->invocation (s/split cmd #"\s+") modules)))

(defn execute-invocation
  "Given a command invocation, the Discord gateway connection, and the message that contains the
   command, executes that command by passing the gateway, message, and arguments to the command
   handler."
  [invocation gateway message]
  (apply (:handler invocation) gateway message (:args invocation)))

(defn prefix-command-tree
  "Helper function for building nested command trees. The first argument is the name of the module or parent command"
  [module-name & children]
  {module-name (apply merge children)})

(defmacro prefix-command
  "Convenience macro for building a prefix command. Input to this should look like:

   (prefix-command
    :command-name [arg1 arg2 arg3]
    (println arg1 arg2 arg3))"
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

(defn clear-handlers!
  "Remove any registered message handlers."
  []
  (reset! message-handlers []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining the global module/command registry
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce installed-prefix-commands (atom {}))

(defn reset-installed-prefix-commands!
  "Remove any registered prefix command handlers."
  []
  (reset! installed-prefix-commands {}))

(defn install-prefix-commands!
  "Given a series of command modules, registers those modules and their commands for use in the bot."
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

(defn load-module-folders!
  "Loads all of the clojure files in the extension folders specified in the config."
  [config]
  (doseq [folder (:extension-folders config)]
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
  "Reload the configured extension folders."
  [gateway message]
  ;; TODO: Check permissions of the user invoking the command
  (reset-installed-prefix-commands!)
  (clear-handlers!)
  (load-module-folders! (:config gateway))
  (register-builtins!)
  (cmds/register-global-commands! gateway (:config gateway))
  (utils/reply-in-channel gateway message "Successfully reloaded all extension folders."))

(defn register-builtins! []
  (install-prefix-commands!
    (prefix-command
      :help [gateway original-message]
      (utils/reply-in-dm gateway original-message (str (keys @installed-prefix-commands))))
    (prefix-command
      :reload [gateway message]
      (reload-all-commands! gateway message))))
