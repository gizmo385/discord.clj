(ns discord.types
  "Types returned from the Discord APIs and proper constructors to transform the API responses
   into the corresponding records"
  (:require [discord.config :as config]
            [clojure.data.json :as json]
            [gniazdo.core :as ws]))

;;; Some global constants
(defonce api-version 6)

;;; General Discord protocols
(defprotocol Authenticated
  (token [this])
  (token-type [this]))


;;; This simple configuration implementation is for bots that just use the standard configuration
;;; file for their Discord auth token. A different Authenticated implementation can be supplied if
;;; desired
(defrecord SimpleAuth [auth-token]
  Authenticated
  (token [_] auth-token)
  (token-type [_] "Bot"))

(defn configuration-auth
  "Creates a Simple authentication record that uses the token specified in the settings file."
  []
  (let [auth-token (config/get-token)]
    (SimpleAuth. auth-token)))


;;; Discord uses Snowflakes for their distinct IDs for messages, users, etc. The protocol,
;;; originally created by Twitter, is documented (as used by Discord) on their developer
;;; documentation here:
;;;   https://discordapp.com/developers/docs/reference#snowflakes
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
    (->snowflake [s] s)
  nil
    (->snowflake [_]
      (println "AHHHHHHHHHHHHHHHH SNOWFLAKE-ABLE THING OF NIL RECIEVED")))

;;; Mapping the returns from the Discord API enumerated types into Clojure keywords
(defonce channel-type
  [:text :private :voice :group])

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
