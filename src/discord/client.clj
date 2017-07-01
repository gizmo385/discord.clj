(ns discord.client
  (:require [clojure.core.async :refer [<! >! close! go go-loop] :as async]
            [clojure.tools.logging :as log]
            [discord.gateway :as gw]
            [discord.http :as http]
            [discord.types :refer [Authenticated Gateway] :as types])
  (:import [discord.types ConfigurationAuth]))

(defprotocol DiscordClient
  (send-message [this channel content options]))

;;; Representing a client connected to the discord server
(defrecord GeneralDiscordClient [auth gateway message-handler send-channel receive-channel
                                 seq-num heartbeat-interval]
  Authenticated
  (token [this]
    (.token (:auth this)))
  (token-type [this]
    (.token-type (:auth this)))

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
         current-user       (http/get-current-user auth)
         seq-num            (atom 0)
         heartbeat-interval (atom nil)
         gateway            (gw/connect-to-gateway auth receive-channel seq-num heartbeat-interval)
         client             (GeneralDiscordClient. auth gateway message-handler send-channel
                                                   receive-channel seq-num heartbeat-interval)]

     (log/info (format "Current user: %s" (with-out-str (prn current-user))))

     ;; Send the identification message to Discord
     (gw/send-identify gateway)

     ;; Read messages coming from the server and pass them to the handler
     (go-loop []
       (if-let [message (<! receive-channel)]
         (do
           (log/info (format "Comparing %s (%s) to %s (%s)"
                             (-> message :author :id)
                             (type (-> message :author :id))
                             (:id current-user)
                             (type (:id current-user))))
           (if (not= (-> message :author :id) (:id current-user)) (do
               (message-handler client message)))))
       (recur))

     ;; Read messages from the send channel and call send-message on them. This allows for
     ;; asynchronous messages sending
     (go-loop []
       (if-let [message (<! send-channel)]
         (send-message client (:channel message) (:content message) (:options message)))
       (recur))

     ;; Return the client that we created
     client)))
