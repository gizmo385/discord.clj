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
;;; Helper functions
;;;
;;; Functions that are used throught the namespace
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord CommandInvocation
  [full-command handler args])

(defn command->invocation
  [command modules]
  (letfn [(split-command->invocation [[next-command-element & rest-of-command] modules]
            (let [layer (get modules (keyword next-command-element))]
              (cond
                (nil? layer) (throw (ex-info "Invalid command!" {:command command}))
                (fn? layer) (->CommandInvocation command layer rest-of-command)
                (map? layer) (split-command->invocation rest-of-command layer))))]
    (split-command->invocation (s/split command #"\s+") modules)))

(defn execute-invocation
  [invocation client message]
  (apply (:handler invocation) client message (:args invocation)))

(defn with-module [module-name & children]
  {module-name (apply merge children)})

(defmacro command
  [command & fn-tail]
  `(hash-map ~command (fn ~(symbol (format "command-invocation-%s" (name command))) ~@fn-tail)))

(comment
  (use 'clojure.pprint)
  (require '[clojure.walk :as w])
  (require '[clojure.zip :as z])
  (let [module-map {:admin {:region {:move (fn [])}
                            :player {:ban (fn [])
                                     :unban (fn [])}}}
        command "admin region move us-west"]
    (command->invocation command module-map))

  (let [invocation (->>
                     (with-module :admin
                       (with-module :region
                         (command
                           :move [client message region]
                           (println (format "Moving the region to %s" region))
                           ))
                       )
                     (command->invocation "admin region move us-west"))]
    (execute-invocation invocation nil nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Custom Discord message handlers
;;;
;;; This allows the creation of more advanced plugins that directly intercept messages that in a
;;; way that allows for more customized handling than what is available through extensions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce message-handlers (atom []))

(defn add-handler! [handler]
  (swap! message-handlers conj handler))

(defn clear-handlers! []
  (reset! message-handlers []))

(defn get-handlers [] @message-handlers)

(defmacro defhandler
  "This is used to create custom message handlers, for which all messages received by the bot will
   be passed through. This is used f or the creation of advanced bot functionality such as automatic
   moderation, alias creation, etc."
  [handler-name [prefix-param client-param message-param] & body]
  (let [handler-fn-name (gensym (name handler-name))]
   `(do
      (timbre/infof "Register custom message handler: %s" ~(name handler-name))
      (defn ~handler-fn-name [~prefix-param ~client-param ~message-param] ~@body)
      (add-handler! ~handler-fn-name))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Defining the global extension registry
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce installed-modules (atom {}))

(defn reset-installed-modules! []
  (reset! installed-modules {}))

(defn install-modules!
  [& new-modules]
  (doseq [nm new-modules]
    (swap! installed-modules merge nm)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Functions to quickly delete or reply to messages from users of the bot
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn build-reply
  [channel reply]
  (if (embeds/embed? reply)
    {:channel channel :content "" :embed reply}
    {:channel channel :content reply}))

(defn reply-in-channel
  [client original-message reply]
  (let [send-channel (:send-channel client)
        message-channel (:channel original-message)]
    (go (>! send-channel (build-reply message-channel reply)))))

(defn reply-in-dm
  [client original-message reply]
  (let [user-id (get-in original-message [:author :id])
        dm-channel (http/create-dm-channel client user-id)
        send-channel (:send-channel client)]
    (go (>! send-channel (build-reply dm-channel reply)))))

(defn delete-original-message
  [client original-message]
  (http/delete-message client (:channel original-message) original-message))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Dispatching messages received by the bot to commands and message handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- dispatch-to-handlers
  "Dispatches to the currently registered user message handlers."
  [prefix client message]
  (let [handlers (get-handlers)]
    (doseq [handler handlers]
      (handler prefix client message))))

(defn- dispatch-to-command-handler
  "Determines the correct handler for a particular command and invokes that handler for the command."
  [client message prefix available-modules]
  (println message)
  (let [trimmed-command-message (trim-command-prefix message prefix)
        invocation (command->invocation (:content trimmed-command-message) available-modules)]
    (try (-> trimmed-command-message
             (get :content)
             (command->invocation available-modules)
             (execute-invocation client message))
         (catch Exception e
           (timbre/errorf "Could not execute command: %s" (:content message))))))

(defn- build-message-handler-fn
  "Builds a handler around a set of modules and commands."
  [prefix]
  (fn [client message]
    ;; If the message starts with the bot prefix, we'll dispatch to the correct handler for
    ;; that command.
    (when (-> message :content (starts-with? prefix))
      (go (dispatch-to-command-handler client message prefix @installed-modules)))

    ;; For every message, we'll dispatch to the handlers. This allows for more sophisticated
    ;; handling of messages that don't necessarily match the prefix (i.e. matching & deleting
    ;; messages with swear words).
    (go (dispatch-to-handlers prefix client message))))

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
      (load-file filename))))

(defn load-module-folders! []
  (doseq [folder (config/get-extension-folders)]
    (load-clojure-files-in-folder! folder))
  (let [installed-module-names (keys @installed-modules)]
    (timbre/infof "Loaded %d extensions: %s."
                  (count installed-module-names)
                  (s/join ", " installed-module-names))))

(declare register-builtins!)

(defn reload-all-commands!
  [client message]
  "Reload the configured extension folders."
  (if (perm/has-permission? client message perm/ADMINISTRATOR)
    (do
      (reset-installed-modules!)
      (clear-handlers!)
      (load-module-folders!)
      (register-builtins!)
      (reply-in-channel client message "Successfully reloaded all extension folders."))
    (reply-in-channel client message "You do not have permission to reload the bot!!")))

(defn register-builtins! []
  (install-modules!
    (command :halt [& _] (System/exit 0))
    (command
      :help [client original-message]
      (reply-in-dm client original-message (str (keys @installed-modules))))
    (command :reload [client message] (reload-all-commands! client message))))

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
  ;; Loads all the clojure files in the folders supplied. Also load the builtin commands.
  (load-module-folders!)
  (register-builtins!)

  ;; Opens a bot with those extensions
  (let [prefix (config/get-prefix)
        bot-name (config/get-bot-name)]
    (with-open [discord-bot (create-bot bot-name prefix)]
      (while true (Thread/sleep 3000)))))
