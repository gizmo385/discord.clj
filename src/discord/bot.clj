(ns discord.bot
  (:require [clojure.core.async :refer [<! >! close! go go-loop] :as async]
            [clojure.tools.logging :as log]
            [discord.gateway :as gw]
            [discord.http :as http]
            [discord.types :refer [Authenticated Gateway] :as types])
  (:import [discord.types ConfigurationAuth]))

(defprotocol DiscordBot
  (send-message [this channel content options]))

;;; Representing a bot connected to the discord server
(defrecord GeneralDiscordBot [auth gateway message-handler send-channel receive-channel
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

  DiscordBot
  (send-message [this channel content options]
    (http/send-message (:auth this) channel content options)))

(defn create-discord-bot
  ([message-handler]
   (create-discord-bot (ConfigurationAuth.) message-handler))

  ([auth message-handler]
   (let [send-channel       (async/chan)
         receive-channel    (async/chan)
         seq-num            (atom 0)
         heartbeat-interval (atom nil)
         gateway            (gw/connect-to-gateway auth receive-channel seq-num heartbeat-interval)
         bot                (GeneralDiscordBot. auth gateway message-handler send-channel
                                                receive-channel seq-num heartbeat-interval)]

     ;; Send the identification message to Discord
     (gw/send-identify gateway)

     ;; Read messages coming from the server and pass them to the handler
     (go-loop []
       (if-let [message (<! receive-channel)]
         (do
           (message-handler bot message)
           (recur))
         (recur)))

     ;; Read messages from the send channel and call send-message on them. This allows for
     ;; asynchronous messages sending
     (go-loop []
       (if-let [message (<! send-channel)]
         (do
           (send-message bot (:channel message) (:content message) (:options message))
           (recur))
         (recur)))

     ;; Return the bot that we created
     bot)))
