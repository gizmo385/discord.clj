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
            [discord.types :refer [Authenticated] :as types]
            [discord.config :as config]))

;;; Representing a message from the API
(defrecord Message [content attachments embeds sent-time channel author user-mentions role-mentions
                    pinned? everyone-mentioned? id])

(defn- convert-long [s]
  (try
    (Long/parseLong s)
    (catch NumberFormatException nfe s)))

(defn build-message
  "Builds a Message record based on the incoming Message from the Discord Gateway. The Gateway
   record that received the message is passed as the second argument to this function."
  [message-map gateway]
  (let [user-wrap (fn [user-map] {:user user-map})
        author    (http/build-user (user-wrap (get-in message-map [:d :author])))
        channel   (http/get-channel gateway (get-in message-map [:d :channel_id]))
        users     (map (comp http/build-user user-wrap) (get-in message-map [:d :mentions]))
        roles     (map (comp http/build-user user-wrap) (get-in message-map [:d :role_mentions]))]
    (map->Message
      {:author                author
       :user-mentions         users
       :role-mentions         roles
       :channel               channel
       :everyone-mentioned?   (get-in message-map [:d :mention_everyone])
       :content               (get-in message-map [:d :content])
       :embeds                (get-in message-map [:d :embeds])
       :attachments           (get-in message-map [:d :attachments])
       :pinned?               (get-in message-map [:d :pinned])
       :id                    (convert-long (get-in message-map [:d :id]))})))

;;; Implementing Discord Gateway behaviour
(defprotocol Gateway
  (send-message [this message]))

(defrecord DiscordGateway [url shards websocket auth seq-num session-id heartbeat-interval]
  java.io.Closeable
  (close [this]
    (if (:websocket this)
      (ws/close (:websocket this))))

  Authenticated
  (token [this]
    (types/token (:auth this)))
  (token-type [this]
     (types/token-type (:auth this)))

  Gateway
  (send-message [this message]
    (ws/send-msg @(:websocket this) (json/write-str message))))

(defn build-gateway [gateway-response]
  (let [gateway-map (into {} gateway-response)
        url (format "%s?v=%s&encoding=%s" (:url gateway-map) types/api-version "json")]
    (map->DiscordGateway (assoc gateway-map :url url))))


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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handling server EVENTS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti handle-server-event
  "Handling server EVENTS.

   Server events are control messages sent by the Gateway to a connected client to inform the
   client about disconnects, reconnects, rate limits, etc."
  (fn [discord-event gateway receive-chan]
    (message-code->name (:op discord-event))))

(defmethod handle-server-event :hello
  [discord-event gateway receive-chan]
  ;;; Handle the initial "HELLO" message, which sets the heartbeat-interval
  (let  [new-heartbeat (get-in discord-event [:d :heartbeat_interval])
         heartbeat-atom (:heartbeat-interval gateway)]
    (log/info (format "Setting heartbeat interval to %d milliseconds" new-heartbeat))
    (reset! heartbeat-atom new-heartbeat)))

;;; Since there is nothing to do regarding a heartback ACK message, we'll just ignore it.
(defmethod handle-server-event :heartbeat-ack [& _])

(defmethod handle-server-event :default
  [discord-event gateway receive-chan]
  (log/info (format "Event of Type: %s" (message-code->name (:op discord-event)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handling server MESSAGES
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti handle-server-message
  "Handling server MESSAGES

   These are messages that are received from the server and include text messages sent by
   users/clients as well as various control messages sent by the Discord gateway."
  (fn [discord-message gateway receive-chan]
    (keyword (:t discord-message))))

;;; These are messages that are currently not explicitly handled by the framework
(defmethod handle-server-message :PRESENCE_UPDATE [& _])
(defmethod handle-server-message :GUILD_CREATE [& _])
(defmethod handle-server-message :TYPING_START [& _])
(defmethod handle-server-message :MESSAGE_DELETE [& _])
(defmethod handle-server-message :MESSAGE_UPDATE [& _])

(defmethod handle-server-message :READY
  [discord-message gateway receive-chan]
  (let [session-id      (get-in discord-message [:d :session-id])
        session-id-atom (:session-id gateway)]
    (reset! session-id-atom session-id)))

(defmethod handle-server-message :HELLO
  [discord-message gateway receive-chan]
  (log/info "RECEIVED HELLO MESSAGE"))

;;; If it's a user message, put it on the receive channel for parsing by the client
(defmethod handle-server-message :MESSAGE_CREATE
  [discord-message gateway receive-chan]
  (let [message (build-message discord-message gateway)]
    (go (>! receive-chan message))))

(defmethod handle-server-message nil
  [discord-message gateway receive-chan]
  ;; A message type of "nil" the message is an event that is handle differently
  (handle-server-event discord-message gateway receive-chan))

(defmethod handle-server-message :default
  [discord-message gateway receive-chan]
  (log/info (format "Unknown message of type %s received: " (keyword (:t discord-message)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helper functions to send some common control messages, such as heartbeats or identification
;;; messages, to the Discord gateway.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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
    (send-message gateway identify)))

(defn send-heartbeat [gateway seq-num]
  (let [heartbeat (format-gateway-message :heartbeat @seq-num)]
    (send-message gateway heartbeat)))

(defn send-resume [gateway session-id seq-num]
  (send-message
    gateway
    (format-gateway-message
      :resume
      {:token           (types/token gateway)
       :session_id      session-id
       :seq             @seq-num})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Establishing a connection to the Discord gateway and begin reading messages from it.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- handle-message
  "Parses a message coming from the server.

   1: Parses the message body
   2: Attempts to update the seq-num atom with the highest available message.
   3: Passes the message received onto the handle-server-message handler. That handler will either
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
      (handle-server-message message gateway receive-channel)))

(defn- create-websocket
  "Creates websocket and connects to the Discord gateway."
  [gateway receive-channel]
  (let [seq-num (:seq-num gateway)
        heartbeat-interval (:heartbeat-interval gateway)
        gateway-url (:url gateway)]
    (ws/connect
      gateway-url
      :on-receive (fn [message]
                    (handle-message message gateway receive-channel))
      :on-connect (fn [message] (log/info "Connected to Discord Gateway"))
      :on-error   (fn [message] (log/error (format "Error: %s" message)))
      :on-close   (fn [status reason] (log/info (format "Closed: %s (%d)" reason status))))))

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
      interval dictated by the Discord Gateway upon connection.
   session-id : (Atom String?) -- An atomic string representing the current session ID for the
      connection
   socket : (Atom Websocket?) -- An atomic websocket representing the current websocket for
      communications"
  [auth receive-channel seq-num-atom heartbeat-interval-atom session-id-atom socket-atom]
  (let [gateway (build-gateway (http/get-bot-gateway auth))
        gateway (assoc gateway
                       :auth auth
                       :session-id session-id-atom
                       :seq-num seq-num-atom
                       :heartbeat-interval heartbeat-interval-atom
                       :receive-channel receive-channel)
        websocket (create-websocket gateway receive-channel)
        gateway (assoc gateway :websocket socket-atom)]

    ;; Assign the correct websocket
    (reset! socket-atom websocket)

    ;; Asynchronously send a heartbeat to the gateway
    (go-loop []
      (send-heartbeat gateway seq-num-atom)
      (Thread/sleep (or @heartbeat-interval-atom 1000))
      (recur))

    ;; Return the gateway that we created
    gateway))

(defn reconnect-gateway [auth gateway]
  (let [receive-channel (:receive-channel gateway)
        socket-atom     (:websocket gateway)
        websocket (create-websocket gateway receive-channel)]
    (reset! socket-atom websocket)))
