(ns discord.gateway
  "This implements the Discord Gateway protocol"
  (:require [clojure.core.async :refer [>! put! close! go go-loop] :as async]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [gniazdo.core :as ws]
            [discord.http :as http]
            [discord.types :refer [Gateway] :as types]
            [discord.config :as config]
            [clojure.pprint :refer [pprint]]))

;;; The different kinds of messages that we can receive from Discord
(defonce server-message-types
  {:dispatch            0
   :heartbeat           1
   :identify            2
   :presence            3
   :voice_state         4
   :voice_ping          5
   :resume              6
   :reconnect           7
   :request_members     8
   :invalidate_session  9
   :hello               10
   :heartbeat_ack       11
   :guild_sync          12})

(defmulti handle-server-message
  "Handle messages coming from Discord across the websocket"
  (fn [discord-message receive-message-channel]
    (:t discord-message)))

;;; If a message type isn't recognized, pass it to the bot client handler
(defmethod handle-server-message :default
  [discord-message receive-message-channel]
  (>! receive-message-channel discord-message))


;;; Sending some of the standard messages to the Discord Gateway
(defn format-gateway-message [op data]
  (if-let [op-code (get server-message-types op)]
    {:op op-code :d data}
    (throw (ex-info "Unknown op-code" {:op-code op}))))

(defn send-identify [gateway]
  (let [identify (format-gateway-message
                   :identify
                   {:token (types/token gateway)
                    :properties {"$os"                "linux"
                                 "$browser"           "discord.clj"
                                 "$device"            "discord.clj"
                                 "$referrer"          ""
                                 "$referring_domain"  ""}
                    :compress false
                    :large_threshold 250
                    :shard [0 (:shards gateway)]})]
    (types/send-msg gateway identify)))

;;; Establishing a connection to the Discord gateway
(defn connect-to-gateway
  "Attempts to connect to the discord Gateway using some supplied authentication source"
  [auth receive-channel]
  (let [gateway (http/get-bot-gateway auth)
        socket  (ws/connect (:url gateway)
                            :on-receive (fn [message]
                                          (-> message
                                              (json/read-str :key-fn keyword)
                                              (handle-server-message receive-channel)))
                            :on-connect (fn [message]
                                          (log/info "Connected to Discord Gateway"))
                            :on-error   (fn [message]
                                          (log/error message))
                            :on-close   (fn [status reason]
                                          (log/info "Closed: %s %d" reason status)))]
    (assoc gateway
           :websocket socket
           :auth      auth)))
