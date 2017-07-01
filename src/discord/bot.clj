(ns discord.bot
  (:require [clojure.string :refer [starts-with?] :as s]
            [clojure.core.async :refer [go >!] :as async]
            [clojure.tools.logging :as log]
            [discord.client :as client]
            [discord.config :as config]
            [discord.types :as types])
  (:import [discord.types ConfigurationAuth]))

(defrecord Extension [command handler])
(defrecord DiscordBot [bot-name extensions prefix client]
  java.io.Closeable
  (close [this]
    (.close (:client this))))

(defn ^:dynamic say
  "The 'say' function does nothing unless invoked in the context of a build-handler-fn where it will
   be locally overriden to send to the channel that the inciting message originated."
  [message-content])

(defn- in-extension-send-message
  [send-channel channel content]
  (go
    (>! send-channel
        {:channel channel
         :content content
         :options {}})))

(defn- build-handler-fn
  "Builds a handler around a set of extensions and rebinds 'say' to send to the message source"
  [send-channel prefix extensions]
  ;; Builds a handler function for a bot that will dispatch messages matching the supplied prefix
  ;; to the handlers of any extensions whose "command" is present immediately after the prefix
  (fn [client message]
    (log/info (format "Message: %s" (with-out-str (prn message))))
    ;; This binding overwrites the 'say' function to respond to the inciting message.
    (binding [say (partial in-extension-send-message send-channel (:channel message))]
      (if (-> message :content (starts-with? prefix))
        (doall
          (for [{:keys [command handler]} extensions]
            (let [command-string  (str prefix command)
                  trimmed-content (-> message
                                      :content
                                      (s/replace-first command-string "")
                                      (s/triml))]
              (if (-> message :content (starts-with? command-string))
                (handler client (assoc message :content trimmed-content))))))))))

(defn create-extension [command handler]
  (Extension. command handler))

;;; Builds a bot based on a name and set of extensions
(defn create-bot
  "Creates a bot that will dynamically dispatch to different extensions based on <prefix><command>
   style messages."
  ([bot-name extensions]
   (create-bot bot-name extensions (config/get-prefix) (ConfigurationAuth.)))

  ([bot-name extensions prefix]
   (create-bot bot-name extensions prefix (ConfigurationAuth.)))

  ([bot-name extensions prefix auth]
   (let [send-channel     (async/chan)
         handler          (build-handler-fn send-channel prefix extensions)
         discord-client   (client/create-discord-client auth handler :send-channel send-channel)]
     (log/info (format "Creating bot with prefix: %s" prefix))
     (DiscordBot. bot-name extensions prefix discord-client))))
