(ns discord.bot
  (:require [clojure.core.async :refer [<! >! close! go go-loop] :as async]
            [discord.gateway :as gw]
            [discord.http :as http]
            [discord.types :refer [Authenticated Gateway] :as types])
  (:import [discord.types ConfigurationAuth]))

(defprotocol DiscordBot
  (send-message [this channel content & options]))

;;; Representing a bot connected to the discord server
(defrecord GeneralDiscordBot [auth gateway message-handler send-channel receive-channel]
  Authenticated
  (token [this]
    (.token (:auth this)))
  (token-type [this]
    (.token-type (:auth this)))

  java.io.Closeable
  (close [this]
    (.close gateway)
    (close! send-channel)
    (close! receive-channel)))

(defn create-discord-bot
  ([message-handler]
   (create-discord-bot (ConfigurationAuth.) message-handler))

  ([auth message-handler]
   (let [send-channel       (async/chan)
         receive-channel    (async/chan)
         gateway            (gw/connect-to-gateway auth receive-channel)]
     (GeneralDiscordBot. auth gateway message-handler send-channel receive-channel))))

