(ns discord.gateway
  "This implements the Discord Gateway protocol"
  (:require [clojure.core.async :refer [>! <! go go-loop] :as async]
            [clojure.data.json :as json]
            [gniazdo.core :as ws]
            [taoensso.timbre :as timbre]
            [discord.http :as http]
            [discord.permissions :as perm]
            [discord.types :as types]
            [discord.config :as config]))

;;; Building a message from a Gateway
(defn build-message
  "Builds a Message record based on the incoming Message from the Discord Gateway. The Gateway
   record that received the message is passed as the second argument to this function."
  [message-map gateway]
  (let [user-wrap (fn [user-map] {:user user-map})
        author    (http/build-user (user-wrap (get-in message-map [:d :author])))
        channel   (http/get-channel gateway (get-in message-map [:d :channel_id]))
        users     (map (comp http/build-user user-wrap)
                       (get-in message-map [:d :mentions]))
        roles     (map (comp http/build-user user-wrap)
                       (get-in message-map [:d :role_mentions]))]
    (types/map->Message
      {:author                author
       :user-mentions         users
       :role-mentions         roles
       :channel               channel
       :everyone-mentioned?   (get-in message-map [:d :mention_everyone])
       :content               (get-in message-map [:d :content])
       :embeds                (get-in message-map [:d :embeds])
       :attachments           (get-in message-map [:d :attachments])
       :pinned?               (get-in message-map [:d :pinned])
       :id                    (Long/parseLong (get-in message-map [:d :id]))})))


;;; Implementing Discord Gateway behaviour
(defprotocol Gateway
  (send-message [this message]))

(defrecord DiscordGateway [url shards websocket auth seq-num session-id
                           heartbeat-interval stop-heartbeat-channel]
  java.io.Closeable
  (close [this]
    (when (:websocket this)
      (ws/close @(:websocket this)))
    (when (:stop-heartbeat-channel this)
      (async/close! (:stop-heartbeat-channel this))))

  types/Authenticated
  (token [this]
    (types/token (:auth this)))
  (token-type [this]
    (types/token-type (:auth this)))

  Gateway
  (send-message [this message]
    (ws/send-msg @(:websocket this) (json/write-str message))))

(defn build-gateway [gateway-response]
  (let [gateway-map (into {} gateway-response)
        url         (format "%s?v=%s&encoding=%s" (:url gateway-map) types/api-version "json")]
    (map->DiscordGateway (assoc gateway-map :url url))))


;;; The different kinds of messages that we can receive from Discord


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handling server EVENTS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti handle-gateway-control-event
  "Handling server control events. Control events are control messages sent by the Gateway to a
   connected client to inform the client about disconnects, reconnects, rate limits, etc."
  (fn [discord-event gateway receive-chan]
    (types/message-code->name (:op discord-event))))

(defmethod handle-gateway-control-event :hello
  [discord-event gateway receive-chan]
  ;;; Handle the initial "HELLO" message, which sets the heartbeat-interval
  (let [new-heartbeat   (get-in discord-event [:d :heartbeat_interval])
        heartbeat-atom  (:heartbeat-interval gateway)]
    (timbre/infof "Setting heartbeat interval to %d milliseconds" new-heartbeat)
    (reset! heartbeat-atom new-heartbeat)))

;;; Since there is nothing to do regarding a heartback ACK message, we'll just ignore it.
(defmethod handle-gateway-control-event :heartbeat-ack [& _])

(defmethod handle-gateway-control-event :default
  [discord-event gateway receive-chan]
  (timbre/infof "Event of Type: %s" (types/message-code->name (:op discord-event))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handles non-control-flow messages from the Discord gateway. Any messages dealing with metadata
;;; surrounding our gateway connection is handled above by handle-gateway-control-event
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti handle-gateway-message
  "Handling messages sent by the Discord API gateway

   These are messages that are received from the gateway and include text messages sent by
   users/clients as well as various control messages sent by the Discord gateway."
  (fn [discord-message gateway receive-chan]
    (keyword (:t discord-message))))

;;; These are messages that are currently not explicitly handled by the framework, but that we don't
;;; want to explicitly handle.
(defmethod handle-gateway-message :PRESENCE_UPDATE [& _])
(defmethod handle-gateway-message :GUILD_CREATE [& _])
(defmethod handle-gateway-message :CHANNEL_CREATE [& _])
(defmethod handle-gateway-message :TYPING_START [& _])
(defmethod handle-gateway-message :MESSAGE_DELETE [& _])
(defmethod handle-gateway-message :MESSAGE_UPDATE [& _])

(defmethod handle-gateway-message :READY
  [discord-message gateway receive-chan]
  (let [session-id      (get-in discord-message [:d :session-id])
        session-id-atom (:session-id gateway)]
    (reset! session-id-atom session-id)))

(defmethod handle-gateway-message :HELLO
  [discord-message gateway receive-chan]
  (timbre/info "RECEIVED HELLO MESSAGE"))

;;; If it's a user message, put it on the receive channel for parsing by the client
(defmethod handle-gateway-message :MESSAGE_CREATE
  [discord-message gateway receive-chan]
  (let [message (build-message discord-message gateway)]
    (go (>! receive-chan message))))

(defmethod handle-gateway-message :GUILD_ROLE_UPDATE
  [discord-message gateway receive-chan]
  (let [guild-id (get-in discord-message [:d :guild_id])
        role-name (get-in discord-message [:d :role :name])]
    (timbre/infof "Role \"%s\" updated, clearing guild role cache for guild %s." role-name guild-id)
    (perm/clear-role-cache! guild-id)))

(defmethod handle-gateway-message :GUILD_ROLE_CREATE
  [discord-message gateway receive-chan]
  (let [guild-id (get-in discord-message [:d :guild_id])
        role-name (get-in discord-message [:d :role :name])]
    (timbre/infof "Role \"%s\" created, clearing guild role cache for guild %s." role-name guild-id)
    (perm/clear-role-cache! guild-id)))

(defmethod handle-gateway-message nil
  [discord-message gateway receive-chan]
  ;; A message type of "nil" the message is an event that is handled differently
  (handle-gateway-control-event discord-message gateway receive-chan))

(defmethod handle-gateway-message :default
  [discord-message gateway receive-chan]
  (timbre/infof "Unknown message of type %s received: %s" (keyword (:t discord-message)) discord-message))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helper functions to send some common control messages, such as heartbeats or identification
;;; messages, to the Discord gateway.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn format-gateway-message
  "Builds the correct map structure with the correct op-codes. If the op-code supplied is not found,
   an (ex-info) Exception will be raised"
  [op data]
  (if-let [op-code (types/message-name->code op)]
    {:op op-code :d data}
    (throw (ex-info "Unknown op-code" {:op-code op}))))

(defn send-identify
  "Sends an identification message to the supplied Gateway. This tells the Discord gateway
   information about ourselves."
  [gateway]
  (->> {:token (types/token gateway)
        :properties {"$os"                "linux"
                     "$browser"           "discord.clj"
                     "$device"            "discord.clj"
                     "$referrer"          ""
                     "$referring_domain"  ""}
        :compress false
        :large_threshold 250
        :shard [0 (:shards gateway)]}
       (format-gateway-message :identify)
       (send-message gateway)))

(defn send-heartbeat [gateway seq-num]
  (let [heartbeat (format-gateway-message :heartbeat @seq-num)]
    (send-message gateway heartbeat)))

(defn send-resume [gateway]
  (let [session-id  (:session-id gateway)
        seq-num     @(:seq-num gateway)]
    (->> {:token           (types/token gateway)
          :session_id      session-id
          :seq             seq-num}
         (format-gateway-message :resume)
         (send-message gateway))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Establishing a connection to the Discord gateway and begin reading messages from it.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- handle-message
  "Parses a message coming from the server.

   1: Parses the message body
   2: Attempts to update the seq-num atom with the highest available message.
   3: Passes the message received onto the handle-gateway-message handler. That handler will either
      directly handle the message, pass it into to a more appropriate handler, or publish it to the
      receive-channel attached to the Gateway."
  [raw-message gateway receive-channel]
  (let [message               (json/read-str raw-message :key-fn keyword)
        next-sequence-number  (:s message)
        seq-num               (:seq-num gateway)]
      ;; Update the sequence number (if present)
      (if next-sequence-number
        (swap! seq-num max next-sequence-number))

      ;; Pass the message on to the handler
      (handle-gateway-message message gateway receive-channel)))

(declare reconnect-gateway)

(defn- create-websocket
  "Creates websocket and connects to the Discord gateway."
  [gateway]
  (let [receive-channel     (:receive-channel gateway)
        gateway-url         (:url gateway)]
    (ws/connect
      gateway-url
      :on-receive (fn [message]
                    (handle-message message gateway receive-channel))
      :on-connect (fn [message] (timbre/info "Connected to Discord Gateway"))
      :on-error   (fn [message] (timbre/errorf "Error: %s" message))
      :on-close   (fn [status reason]
                    ;; The codes above 1001 denote erroreous closure states
                    ;; https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent
                    (if (> 1001 status)
                      (do
                        (timbre/warnf "Socket closed for unexpected reason (%d): %s" status reason)
                        (timbre/warnf "Attempting to reconnect to websocket...")
                        (reconnect-gateway gateway))
                      (timbre/infof "Closing Gateway websocket, not reconnecting (%d)." status))))))

;;; There are a few elements of state that a Discord gateway connection needs to track, such as
;;; its sequence number, its heartbeat interval, the websocket connection, and its I/O channels.
(defn connect-to-gateway
  "Attempts to connect to the discord Gateway using some supplied authentication source

   Arguments:
   auth : Authenticated -- An implementation of the Authenticated protcol to authenticate with the
      Discord APIs.
   receive-channel : Channel -- An asynchronous channel (core.async) that messages from the server
      will be pushed onto."
  [auth receive-channel]
  (let [socket                 (atom nil)
        seq-num                (atom 0)
        heartbeat-interval     (atom 1000)
        stop-heartbeat-channel (async/chan)
        session-id             (atom nil)
        gateway                (build-gateway (http/get-bot-gateway auth))
        gateway                (assoc gateway
                                      :auth                   auth
                                      :session-id             session-id
                                      :seq-num                seq-num
                                      :heartbeat-interval     heartbeat-interval
                                      :stop-heartbeat-channel stop-heartbeat-channel
                                      :receive-channel        receive-channel
                                      :websocket              socket)
        websocket              (create-websocket gateway)]

    ;; Assign the connected websocket to the Gateway's socket field
    (reset! socket websocket)

    ;; Begin asynchronously sending heartbeat messages to the gateway
    (go-loop []
      (send-heartbeat gateway seq-num)
      (async/alt!
        stop-heartbeat-channel (timbre/warn "Websocket closed! Terminating heartbeat channel...")
        (async/timeout @heartbeat-interval) (recur)))

    ;; Return the gateway that we created
    gateway))

(defn reconnect-gateway
  "In the event that we are disconnected from the Discord gateway, we will attempt to reconnect by
   establishing a new websocket connection with the gateway. After this is complete, we will update
   our current socket reference and then send a 'resume' message to the Discord gateway that we have
   resumed our session."
  [gateway]
  (let [socket        (:websocket gateway)
        websocket     (create-websocket gateway)]
    (reset! socket websocket)
    (send-resume gateway)))
