(ns discord.gateway
  "This implements the Discord Gateway protocol"
  (:require [clojure.core.async :refer [>! put! close! go go-loop] :as async]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.set :refer [map-invert]]
            [clojure.pprint :refer [pprint]]
            [gniazdo.core :as ws]
            [swiss.arrows :refer [-<> -<>>]]
            [discord.http :as http]
            [discord.types :refer [Authenticated Gateway] :as types]
            [discord.config :as config])
  (:import [discord.types DiscordGateway]))


;;; Implementing Discord Gateway behaviour
(extend-type DiscordGateway
  Authenticated
  (token [this]
    (.token (:auth this)))
  (token-type [this]
    (.token-type (:auth this)))

  Gateway
  (send-message [this message]
    (ws/send-msg (:websocket this) (json/write-str message))))

;;; The different kinds of messages that we can receive from Discord
(defonce message-name->code
  {:dispatch            0
   :heartbeat           1
   :identify            2
   :presence            3
   :voice-state         4
   :voice-ping          5
   :resume              6
   :reconnect           7
   :request-members     8
   :invalidate-session  9
   :hello               10
   :heartbeat-ack       11
   :guild-sync          12})

(defonce message-code->name
  (map-invert message-name->code))

;;; Handle server message events
(defmulti handle-server-event
  (fn [discord-event receive-chan seq-num heartbeat-interval]
    (message-code->name (:op discord-event))))

(defmethod handle-server-event :hello
  [discord-event receive-chan seq-num heartbeat-interval]
  (let  [new-heartbeat (get-in discord-event [:d :heartbeat_interval])]
    (log/info (format "Setting heartbeat interval to %d seconds" new-heartbeat))
    (reset! heartbeat-interval new-heartbeat)))

(defmethod handle-server-event :default
  [discord-event receive-chan seq-num heartbeat-interval]
  (log/info
    (format "Event of Type: %s" (message-code->name (:op discord-event)))))

;;; Handle messages from the server
(defmulti handle-server-message
  "Handle messages coming from Discord across the websocket"
  (fn [discord-message receive-chan seq-num heartbeat-interval]
    (keyword (:t discord-message))))

(defmethod handle-server-message :HELLO
  [discord-message receive-chan seq-num _]
  (log/info "RECEIVED HELLO MESSAGE"))

;;; If it's a user message, put it on the receive channel for parsing by the client
(defmethod handle-server-message :MESSAGE_CREATE
  [discord-message receive-chan seq-num _]
  (let [message (types/build-message discord-message)]
    (go (>! receive-chan message))))

(defmethod handle-server-message :READY
  [discord-message receive-chan seq-num _]
  (log/info (format "READY: %s" (with-out-str (prn discord-message)))))

;;; A message type of "nil" or "null" indicates the message is an event and should be handled
;;; differently
(defmethod handle-server-message nil
  [discord-message receive-chan seq-num heartbeat-interval]
  (handle-server-event discord-message receive-chan seq-num heartbeat-interval))

(defmethod handle-server-message :default
  [discord-message receive-chan seq-num _]
  (if (:t discord-message)
    (log/info
      (format "Unknown message of type %s received by the client" (keyword (:t discord-message))))
    (log/info
      (format "Unknown message %s" discord-message))))


;;; Sending some of the standard messages to the Discord Gateway
(defn format-gateway-message [op data]
  (if-let [op-code (message-name->code op)]
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
    (types/send-message gateway identify)))

(defn send-heartbeat [gateway seq-num]
  (let [heartbeat (format-gateway-message :heartbeat @seq-num)]
    (log/info (format "Sending heartbeat: %s" heartbeat))
    (types/send-message gateway heartbeat)))



;;; Establishing a connection to the Discord gateway
(defn- handle-message [raw-message gateway receive-channel seq-num heartbeat-interval]
  (let [message               (json/read-str raw-message :key-fn keyword)
        next-sequence-number  (:s message)]
      ;; Update the sequence number (if present)
      (if next-sequence-number
        (swap! seq-num max next-sequence-number))

      ;; Pass the message on to the handler
      (handle-server-message message receive-channel seq-num heartbeat-interval)))

(defn- create-websocket [gateway receive-channel seq-num heartbeat-interval]
  (ws/connect
    (:url gateway)
    :on-receive (fn [message]
                  (handle-message message gateway receive-channel seq-num heartbeat-interval))
    :on-connect (fn [message]
                  (log/info "Connected to Discord Gateway"))
    :on-error   (fn [message]
                  (log/error (format "Error: %s" message)))
    :on-close   (fn [status reason]
                  (log/info (format "Closed: %s (%d)" reason status)))))

(defn connect-to-gateway
  "Attempts to connect to the discord Gateway using some supplied authentication source"
  [auth receive-channel seq-num heartbeat-interval]
  (let [gateway (http/get-bot-gateway auth)
        socket  (create-websocket gateway receive-channel seq-num heartbeat-interval)
        gateway (assoc gateway
                       :websocket socket
                       :auth      auth)]

    ;; Asynchronously send a heartbeat to the gateway
    (go-loop []
      (send-heartbeat gateway seq-num)
      (Thread/sleep (or @heartbeat-interval 1000))
      (recur))

    ;; Return the gateway that we created
    gateway))
