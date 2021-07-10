(ns discord.bot
  (:require [clojure.string :refer [starts-with? ends-with?] :as s]
            [clojure.java.io :as io]
            [clojure.core.async :refer [go >!] :as async]
            [taoensso.timbre :as timbre]
            [discord.client :as client]
            [discord.config :as config]
            [discord.embeds :as embeds]
            [discord.http :as http]
            [discord.permissions :as perm]
            [discord.interactions.slash :as slash]
            [discord.utils :as utils]
            [discord.types :as types]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helper functions
;;;
;;; Functions that are used throught the namespace
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- trim-command-prefix
  "Trims the prefix from a command."
  [message prefix]
  (assoc message :content (-> message :content (s/replace-first prefix "") s/triml)))

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
  [invocation client message]
  (apply (:handler invocation) client message (:args invocation)))

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
   where the first argument is the bot's prefix, the second is the Discord client and the third
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
;;; Functions to quickly delete or reply to messages from users of the bot
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn build-reply
  [channel reply components embed]
  (let [content (cond-> {:content reply}
                  (some? components) (assoc :components components)
                  (some? embed) (assoc :embeds embed))]
    {:channel channel :content content}))

(defn reply-in-channel
  "Sends a message in response to a message from a user in the channel the message originated in.

   Optionally, message components or embeds can be included."
  ([client original-message reply]
   (reply-in-channel client original-message reply nil nil))
  ([client original-message reply components]
   (reply-in-channel client original-message reply components nil))
  ([client original-message reply components embed]
   (let [send-channel (:send-channel client)
         message-channel (:channel original-message)]
     (go (>! send-channel (build-reply message-channel reply components embed))))))

(defn reply-in-dm
  "Sends a reply back to the message sender in a DM channel.

   Optionally, message components and embeds can be included."
  ([client original-message reply]
   (reply-in-dm client original-message reply nil nil))
  ([client original-message reply components]
   (reply-in-dm client original-message reply components nil))
  ([client original-message reply components embed]
   (let [user-id (get-in original-message [:author :id])
         dm-channel (http/create-dm-channel client user-id)
         send-channel (:send-channel client)]
     (go (>! send-channel (build-reply dm-channel reply components embed))))))

(defn delete-original-message
  [client original-message]
  (http/delete-message client (:channel original-message) original-message))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Dispatching messages received by the bot to commands and message handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- dispatch-to-message-handlers
  "Dispatches to the currently registered user message handlers."
  [prefix client message]
  (doseq [handler @message-handlers]
    (handler prefix client message)))

(defn- dispatch-to-command-handler
  "Determines the correct handler for a particular command and invokes that handler for the command."
  [client message prefix available-modules]
  (try
    (let [trimmed-command-message (trim-command-prefix message prefix)
          invocation (command->invocation (:content trimmed-command-message) available-modules)]
      (some-> trimmed-command-message
              (get :content)
              (command->invocation available-modules)
              (execute-invocation client message)))
    (catch clojure.lang.ArityException e
      (timbre/error e (format "Wrong number of arguments for command: %s" (:content message)))
      (reply-in-channel client message "Wrong number of arguments supplied for command."))
    (catch Exception e
      (timbre/error e (format "Error executing command: %s" (:content message)))
      (reply-in-channel client message "Error while executing command :("))))

(defn- build-message-handler-fn
  "Builds a handler around a set of modules and commands."
  [prefix]
  (fn [client message]
    ;; If the message starts with the bot prefix, we'll dispatch to the correct handler for
    ;; that command.
    (when (-> message :content (starts-with? prefix))
      (go (dispatch-to-command-handler client message prefix @installed-prefix-commands)))

    ;; For every message, we'll dispatch to the handlers. This allows for more sophisticated
    ;; handling of messages that don't necessarily match the prefix (i.e. matching & deleting
    ;; messages with swear words).
    (go (dispatch-to-message-handlers prefix client message))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Loading bots into the system
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-clojure-files
  "Given a directory, returns all '.clj' files in that folder."
  [folder]
  (->> folder
       (io/file)
       (file-seq)
       (filter (fn [f] (ends-with? f ".clj")))
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

(declare register-builtins!)

(defn reload-all-commands!
  [client message]
  "Reload the configured extension folders."
  (do
      (reset-installed-prefix-commands!)
      (clear-handlers!)
      (load-module-folders!)
      (register-builtins!)
      (slash/register-global-commands! (types/configuration-auth))
      (reply-in-channel client message "Successfully reloaded all extension folders."))
  #_(if (perm/has-permission? client message perm/ADMINISTRATOR)
    (do
      (reset-installed-prefix-commands!)
      (clear-handlers!)
      (load-module-folders!)
      (register-builtins!)
      (reply-in-channel client message "Successfully reloaded all extension folders."))
    (reply-in-channel client message "You do not have permission to reload the bot!!")))

(defn register-builtins! []
  (install-prefix-commands!
    (prefix-command
      :help [client original-message]
      (reply-in-dm client original-message (str (keys @installed-prefix-commands))))
    (prefix-command :reload [client message] (reload-all-commands! client message))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Bot Creation
;;;
;;; Defining a Discord bot as a closeable object, where closing the bot has the effect of closing
;;; the connect to the Discord servers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord DiscordBot [bot-name prefix client running?]
  java.io.Closeable
  (close [this]
    (reset! (:running? this) false)
    (.close (:client this))))

(defn create-bot
  "Creates a bot that will dynamically dispatch to different extensions based on <prefix><command>
   style messages."
  ([bot-name]
   (create-bot bot-name (config/get-prefix) (types/configuration-auth)))

  ([bot-name prefix]
   (create-bot bot-name prefix (types/configuration-auth)))

  ([bot-name prefix auth]
   (let [message-handler (build-message-handler-fn prefix)
         discord-client (client/create-discord-client auth message-handler)]
     (timbre/infof "Creating bot with prefix: %s" prefix)
     (->DiscordBot bot-name prefix discord-client (atom true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Starting the bot
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-bot!
  "Creates a bot where the extensions are those present in all Clojure files present in the
   directories supplied. This allows you to dynamically add files to a extensions/ directory and
   have them get automatically loaded by the bot when it starts up."
  []
  ;; Loads all the clojure files in the folders supplied. Then load the builtin commands register
  ;; the slash commands with the API
  (load-module-folders!)
  (register-builtins!)
  (slash/register-global-commands! (types/configuration-auth))

  ;; Opens a bot with those extensions
  (let [prefix (config/get-prefix)
        bot-name (config/get-bot-name)]
    (with-open [discord-bot (create-bot bot-name prefix)]
      (while true (Thread/sleep 3000)))))
