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
(defrecord ConfigurationAuth []
  Authenticated
  (token [_] (config/get-token))
  (token-type [_] "Bot"))

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
