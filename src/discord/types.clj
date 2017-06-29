(ns discord.types
  "Types returned from the Discord APIs and proper constructors to transform the API responses
   into the corresponding records"
  (:require [discord.config :as config]))

;;; General Discord protocols
(defprotocol Authenticated
  (token [this])
  (token-type [this]))

(defn build-auth-string [auth]
  (format "%s %s" (token-type auth) (token auth)))

;;; This simple configuration implementation is for bots that just use the standard configuration
;;; file for their Discord auth token. A different Authenticated implementation can be supplied if
;;; desired
(defrecord ConfigurationAuth []
  Authenticated
  (token [_] (config/get-token))
  (token-type [_] "Bot"))

;;; Representing a Discord Gateway
(defprotocol Gateway
  (send-msg [this message]))

(defrecord DiscordGateway [url shards handler websocket auth]
  Authenticated
  (token [this] (.token auth))
  (token-type [this] (.token-type auth)))

(defn build-gateway [gateway-response]
  (map->DiscordGateway (into {} gateway-response)))


(defprotocol DiscordBot
  (send-message [this channel content & options]))

;;; Representing a bot connected to the discord server
(defrecord GeneralDiscordBot [auth gateway message-handler send-channel receive-channel]
  Authenticated
  (token [this]
    (.token (:auth this)))
  (token-type [this]
    (.token-type (:auth this)))

  Gateway
  (send-msg [this message]
    (.send-msg (:gateway this) message))
  
  (GeneralDiscordBot)
  )


;;; Representing a Discord User
(defrecord User [id username roles deaf mute avatar joined distriminator])

(defn build-user [user-map]
  (map->User
    {:deaf          (:deaf user-map)
     :mute          (:mute user-map)
     :roles         (:roles user-map)
     :joined        (:joined_at user-map)
     :username      (-> user-map :user :username)
     :id            (-> user-map :user :id)
     :avatar        (-> user-map :user :avatar)
     :discriminator (-> user-map :user :discriminator)}))

;;; Representing a Discord Channel
(defrecord Channel [id guild-id name type position topic])

(defonce channel-type-map
  {0      :text
   :text  :text
   2      :voice
   :voice :voice})

(defn build-channel [channel-map]
  (map->Channel
    {:guild-id  (:guild_id channel-map)
     :name      (:name channel-map)
     :topic     (:topic channel-map)
     :position  (:position channel-map)
     :id        (:id channel-map)
     :type      (get channel-type-map (:type channel-map))}))


;;; Representing a Discord Server
(defrecord Server [id name permissions owner? icon])

(defn build-server [server-map]
  (map->Server
    {:id          (:id server-map)
     :name        (:name server-map)
     :owner?      (:owner server-map)
     :icon        (:icon server-map)
     :permissions (:permissions server-map)}))

;;; Mapping the returns from the Discord API enumerated types into Clojure keywords
(defonce channel-type
  [:text :private :voice :group])

(defonce message-type
  [:default :recipient-add :recpient-remove :call :channel-name-change
   :channel-icon-change :pins-add])

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

