(ns discord.client
  (:require
    [clojure.core.async :refer [<! >! close! go go-loop] :as async]
    [clojure.data.json :as json]
    [taoensso.timbre :as timbre]
    [discord.gateway :refer [Gateway] :as gw]
    [discord.http :as http]
    [discord.types :refer [Authenticated] :as types]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Representing a Discord client connected to the Discord server
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol DiscordClient
  (send-message [this channel content]))

(defrecord GeneralDiscordClient [auth gateway message-handler send-channel receive-channel]
  Authenticated
  (token [this]
    (types/token (:auth this)))
  (token-type [this]
    (types/token-type (:auth this)))

  java.io.Closeable
  (close [this]
    (.close gateway)
    (close! send-channel)
    (close! receive-channel))

  DiscordClient
  (send-message [this channel content]
    (http/send-message (:auth this) channel content)))


(defn create-discord-client
  "Creates a simple client to communicate with the Discord APIs and Gateway. The client handles all
   server messages via the Gateway. It will handle the server events and manage the asynchronous
   communication channels, as well as the sending of identification messages.

   Required argument:
      message-handler : Function taking a DiscordClient and a Message that handles the messages
        getting pushed onto the client's receive channel.

   Optional arguments:
      auth : Authenticated - An object which can generate authentication tokens for Discord's APIs.
        If this argument is not supplied, authentication information from the configuration files
        will be used instead.

   Additional options available:
      send-channel : Channel - The asynchronous channel to send messages to.
      receive-channel : Channel - The asynchronous channel to send messages from."
  ([message-handler]
   (let [default-auth (types/configuration-auth)]
     (create-discord-client default-auth message-handler)))

  ([auth message-handler & {:keys [send-channel receive-channel] :as options}]
   (let [send-chan  (or (:send-channel options) (async/chan))
         recv-chan  (or (:receive-channel options) (async/chan))
         gateway    (gw/connect-to-gateway auth recv-chan)
         client     (GeneralDiscordClient. auth gateway message-handler send-chan recv-chan)]

     ;; Send the identification message to Discord
     (gw/send-identify gateway)

     ;; Read messages coming from the server and pass them to the handler
     (go-loop []
       (when-let [message (<! recv-chan)]
         (if (-> message :author :bot? not)
           (try
             (message-handler client message)
             (catch Exception e (timbre/errorf "Error handling message: %s" e))))
         (recur)))

     ;; Read messages from the send channel and call send-message on them. This allows for
     ;; asynchronous messages sending
     (go-loop []
       (when-let [{:keys [channel content]} (<! send-chan)]
         (try
           (send-message client channel content)
           (catch Exception e (timbre/errorf "Error sending message: %s" e)))
         (recur)))

     ;; Return the client that we created
     client)))
