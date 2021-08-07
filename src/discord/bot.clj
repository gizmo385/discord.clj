(ns discord.bot
  (:require
    [clojure.core.async :refer [<! >! close! go go-loop] :as async]
    [clojure.string :as s]
    [discord.gateway :as gw]
    [discord.types.auth :as a]
    [discord.extensions.core :as ext]
    [discord.extensions.utils :as ext-utils]
    [discord.interactions.slash :as slash]
    [integrant.core :as ig]
    [taoensso.timbre :as timbre]))

(defrecord Bot
  [config gateway message-handlers]
  a/Authenticated
  (token-type [_] (a/token-type gateway))
  (token [_] (a/token gateway)))

(defn- trim-command-prefix
  "Trims the prefix from a command."
  [message prefix]
  (assoc message :content (-> message :content (s/replace-first prefix "") s/triml)))

(defn- dispatch-to-message-handlers
  [config gateway message]
  (doseq [handler @ext/message-handlers]
    handler (:prefix config) gateway message))

(defn- dispatch-to-command-handlers
  [config gateway message]
  (try
    (let [installed-commands @ext/installed-prefix-commands
          trimmed-command-message (trim-command-prefix message (:prefix config))
          invocation (ext/command->invocation (:content trimmed-command-message) installed-commands)]
      (some-> trimmed-command-message
              (get :content)
              (ext/command->invocation installed-commands)
              (ext/execute-invocation gateway message)))
    (catch clojure.lang.ArityException e
      (timbre/error e (format "Wrong number of arguments for command: %s" (:content message)))
      (ext-utils/reply-in-channel gateway message "Wrong number of arguments supplied for command."))
    (catch Exception e
      (timbre/error e (format "Error executing command: %s" (:content message)))
      (ext-utils/reply-in-channel gateway message "Error while executing command :("))))

(defmethod ig/init-key :discord/bot
  [_ {:keys [config gateway]}]
  (let [handlers (atom [])
        prefix (:prefix config)
        bot (->Bot config gateway handlers) ]
    (ext/load-module-folders! config)
    (ext/register-builtins!)
    (slash/register-global-commands! (:auth gateway) config)

    (go-loop []
      (when-let [message (<! (get-in gateway [:metadata :recv-chan]))]
        (when-not (-> message :author :bot?)
          (when (-> message :content (s/starts-with? (:prefix config)))
            (go (dispatch-to-command-handlers config gateway message)))
          (go (dispatch-to-message-handlers config gateway message))))
      (recur))

    bot))
