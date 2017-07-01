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

;;; Handle the initial "HELLO" message
(defmethod handle-server-event :hello
  [discord-event receive-chan seq-num heartbeat-interval]
  (let  [new-heartbeat (get-in discord-event [:d :heartbeat_interval])]
    (log/info (format "Setting heartbeat interval to %d milliseconds" new-heartbeat))
    (reset! heartbeat-interval new-heartbeat)))

;;; Since there is nothing to do regarding a heartback ACK message, we'll just ignore it.
(defmethod handle-server-event :heartbeat-ack [& _])

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

(defmethod handle-server-message nil
  [discord-message receive-chan seq-num heartbeat-interval]
  ;; A message type of "nil" the message is an event that is handle differently
  (handle-server-event discord-message receive-chan seq-num heartbeat-interval))

(defmethod handle-server-message :default
  [discord-message receive-chan seq-num _]
  (if (:t discord-message)
    (log/info
      (format "Unknown message of type %s received by the client" (keyword (:t discord-message))))
    (log/info
      (format "Unknown message %s" discord-message))))


;;; Sending some of the standard messages to the Discord Gateway
(defn format-gateway-message
  "Builds the correct map structure with the correct op-codes. If the op-code supplied is not found,
   an (ex-info) Exception will be raised"
  [op data]
  (if-let [op-code (message-name->code op)]
    {:op op-code :d data}
    (throw (ex-info "Unknown op-code" {:op-code op}))))

(defn send-identify
  "Sends an identification message to the supplied Gateway. This tells the Discord gateway
   information about ourselves."
  [gateway]
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
    (types/send-message gateway heartbeat)))



;;; Establishing a connection to the Discord gateway
(defn- handle-message
  "Parses a message coming from the server.

   1: Parses the message body
   2: Attempts to update the seq-num atom with the highest available message.
   3: Passes the message received onto the handle-server-message handler. That handler will either
      directly handle the message, pass it into to a more appropriate handler, or publish it to the
      receive-channel attached to the Gateway."
  [raw-message gateway receive-channel seq-num heartbeat-interval]
  (let [message               (json/read-str raw-message :key-fn keyword)
        next-sequence-number  (:s message)]
      ;; Update the sequence number (if present)
      (if next-sequence-number
        (swap! seq-num max next-sequence-number))

      ;; Pass the message on to the handler
      (handle-server-message message receive-channel seq-num heartbeat-interval)))

(defn- create-websocket
  "Creates websocket and connects to the Discord gateway."
  [gateway receive-channel seq-num heartbeat-interval]
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
  "Attempts to connect to the discord Gateway using some supplied authentication source

   Arguments:
   auth : Authenticated -- An implementation of the Authenticated protcol to authenticate with the
      Discord APIs
   receive-channel : Channel -- An asynchronous channel (core.async) that messages from the server
      will be pushed onto
   seq-num : (Atom Integer?) -- An atomic integer representing the most recent sequence number
      the gateway has seen from the server.
   heartbeat-interval : (Atom Integer?) -- An atomic integer representing the current heartbeat
      interval dictated by the Discord Gateway upon connection."
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
