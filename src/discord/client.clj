(ns discord.client
  (:require [clojure.core.async :refer [<! >! close! go go-loop] :as async]
            [clojure.tools.logging :as log]
            [discord.gateway :refer [Gateway] :as gw]
            [discord.http :as http]
            [discord.types :refer [Authenticated] :as types])
  (:import [discord.types ConfigurationAuth]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Representing a Discord client connected to the Discord server
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol DiscordClient
  (send-message [this channel content options]))

(defrecord GeneralDiscordClient [auth gateway message-handler send-channel receive-channel
                                 seq-num heartbeat-interval]
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
  (send-message [this channel content options]
    (apply http/send-message (:auth this) channel content options)))

(defn create-discord-client
  "Creates a simple client to communicate with the Discord APIs and Gateway. The client handles all
   server messages via the Gateway. It will handle the server events and manage the asynchronous
   communication channels, as well as the sending of identification messages.

   Valid options are:
   send-channel : Channel - The asynchronous channel to send messages to.
   receive-channel : Channel - The asynchronous channel to send messages from."
  ([message-handler]
   (create-discord-client (ConfigurationAuth.) message-handler))

  ([auth message-handler & {:keys [send-channel receive-channel] :as options}]
   (let [send-channel       (or (:send-channel options) (async/chan))
         receive-channel    (or (:receive-channel options) (async/chan))
         seq-num            (atom 0)
         heartbeat-interval (atom nil)
         session-id         (atom nil)
         socket             (atom nil)
         gateway            (gw/connect-to-gateway auth receive-channel seq-num heartbeat-interval
                                                   session-id socket)
         client             (GeneralDiscordClient. auth gateway message-handler send-channel
                                                   receive-channel seq-num heartbeat-interval)]

     ;; Send the identification message to Discord
     (gw/send-identify gateway)

     ;; Read messages coming from the server and pass them to the handler
     (go-loop []
       (if-let [message (<! receive-channel)]
         (if (-> message :author :bot? not)
           (try
             (message-handler client message)
             (catch Exception e (log/error e)))))
       (recur))

     ;; Read messages from the send channel and call send-message on them. This allows for
     ;; asynchronous messages sending
     (go-loop []
       (if-let [message (<! send-channel)]
         (send-message client (:channel message) (:content message) (:options message)))
       (recur))

     ;; Return the client that we created
     client)))
