(ns discord.bot
  (:require [clojure.string :refer [starts-with?] :as s]
            [clojure.core.async :refer [go >!] :as async]
            [clojure.tools.logging :as log]
            [discord.client :as client]
            [discord.http :as http]
            [discord.config :as config]
            [discord.types :as types])
  (:import [discord.types ConfigurationAuth]))

;;; Defining what an Extension and a Bot is
(defrecord Extension [command handler])
(defrecord DiscordBot [bot-name extensions prefix client]
  java.io.Closeable
  (close [this]
    (.close (:client this))))

;;; Helper functions
(defn- trim-message-command
  "Trims the prefix and command from the helper function."
  [message command]
  (assoc message :content (-> message :content (s/replace-first command "") s/triml)))

;;; Patch functions that get exposed dynamically to clients within bot handler functions. These
;;; functions are nil unless invoked within the context of a function called from within a
;;; build-handler-fn.
(defn ^:dynamic say [message])
(defn ^:dynamic delete [message])

;;; These functions locally patch the above functions based on context supplied by the handler
(defn- say*
  [send-channel channel content]
  (go (>! send-channel {:channel channel :content content :options {}})))

;;; Bot extension handling
(defn- send-general-help-message
  [client extensions])

(defn- dispatch-to-handlers
  "Dispatches to relevant function handlers when the the messages starts with <prefix><command>."
  [client message prefix extensions]
  (for [{:keys [command handler] :as ext} extensions
        :let  [command-string (str prefix command)]
        :when (starts-with? (:content message) command-string)]
    (handler client (trim-message-command message command-string))))

(defn- build-handler-fn
  "Builds a handler around a set of extensions and rebinds 'say' to send to the message source"
  [prefix extensions]
  ;; Builds a handler function for a bot that will dispatch messages matching the supplied prefix
  ;; to the handlers of any extensions whose "command" is present immediately after the prefix
  (fn [client message]
    ;; First we'll overwrite all of the dynamic functions
    (binding [say     (partial say* (:send-channel client) (:channel message))
              delete  (partial http/delete-message client (:channel message))]
      (if (-> message :content (starts-with? prefix))
        (if (empty? (dispatch-to-handlers client message prefix extensions))
          (send-general-help-message client extensions))))))


;;; Builds a bot based on a name and set of extensions
(defn create-extension [command handler]
  (Extension. command handler))

(defn create-bot
  "Creates a bot that will dynamically dispatch to different extensions based on <prefix><command>
   style messages."
  ([bot-name extensions]
   (create-bot bot-name extensions (config/get-prefix) (ConfigurationAuth.)))

  ([bot-name extensions prefix]
   (create-bot bot-name extensions prefix (ConfigurationAuth.)))

  ([bot-name extensions prefix auth]
   (let [handler          (build-handler-fn prefix extensions)
         discord-client   (client/create-discord-client auth handler)]
     (log/info (format "Creating bot with prefix: %s" prefix))
     (DiscordBot. bot-name extensions prefix discord-client))))


;;; Macros to simplify bot creation
(defn build-extensions
  ([fn-key fn-body]
   `(Extension. (name ~fn-key) ~fn-body))
  ([key-func-pairs]
   (into [] (map (partial apply build-extensions)
                 (partition 2 key-func-pairs)))))

(defmacro open-with-cogs [bot-name prefix & key-func-pairs]
  `(with-open [discord-bot# (create-bot ~bot-name ~(build-extensions key-func-pairs) ~prefix)]
     (while true (Thread/sleep 3000))))
