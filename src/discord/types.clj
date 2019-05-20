(ns discord.types
  "General data types that are useful throughout Discord.cl"
  (:require [discord.config :as config]
            [clojure.set :refer [map-invert]]
            [clojure.data.json :as json]
            [gniazdo.core :as ws]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Authentication
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol Authenticated
  (token [this])
  (token-type [this]))

(defrecord SimpleAuth [auth-token]
  Authenticated
  (token [_] auth-token)
  (token-type [_] "Bot"))

(defn configuration-auth
  "Creates a Simple authentication record that uses the token specified in the settings file."
  []
  (let [auth-token (config/get-token)]
    (SimpleAuth. auth-token)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; The Discord Snowflake Protocol
;;;
;;; Discord uses Snowflakes for their distinct IDs for messages, users, etc. The protocol,
;;; originally created by Twitter, is documented (as used by Discord) on their developer
;;; documentation here:
;;;   https://discordapp.com/developers/docs/reference#snowflakes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol Snowflake
  "Defining types which are Snowflakes according to the Discord API. These are objects which have
   unique IDs in the Discord API"
  (->snowflake [value] "Generate a Snowflake ID for the Discord APIs"))

;;; Several built-in types can be used as snowflakes
(extend-protocol Snowflake
  Integer
    (->snowflake [i] i)
  Long
    (->snowflake [l] l)
  String
    (->snowflake [s] s))

(defrecord Message [content attachments embeds sent-time channel author user-mentions
                    role-mentions pinned? everyone-mentioned? id]
  Snowflake
  (->snowflake [message] (:id message)))

(defrecord Server [id name permissions owner? icon region]
  Snowflake
  (->snowflake [server] (-> server :id Long/parseLong)))

(defrecord User [id username mention bot? mfa-enabled? verified? roles deaf mute
                 avatar joined discriminator]
  Snowflake
  (->snowflake [user] (-> user :id Long/parseLong)))

(defrecord Channel [id guild-id name type position topic]
  Snowflake
  (->snowflake [channel] (-> channel :id Long/parseLong)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; General Discord constant information
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce api-version 6)
(defonce voice-gateway-version 3)

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

(defonce channel-classifier
  [:text :private :voice :group])

(defonce channel-type
  {0      :text
   :text  :text
   2      :voice
   :voice :voice})

(defonce verification-level
  [:none :low :medium :high :table-flip])

(defonce server-region
  {"us-west"       :us-west
   "us-east"       :us-east
   "us-south"      :us-south
   "us-central"    :us-central
   "eu-west"       :eu-west
   "eu-central"    :eu-central
   "singapore"     :singapore
   "london"        :london
   "sydney"        :sydney
   "amsterdam"     :amsterdam
   "frankfurt"     :frankfurt
   "brazil"        :brazil
   "vip-us-east"   :vip-us-east
   "vip-us-west"   :vip-us-west
   "vip-amsterdam" :vip-amsterdam})

(defonce status
  {"online"         :online
   "offline"        :offline
   "idle"           :idle
   "do_not_disturb" :dnd
   "invisible"      :invisible})

(defonce default-avatar
  [:blurple :gray :green :orange :red])
