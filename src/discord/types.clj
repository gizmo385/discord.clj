(ns discord.types
  "Types returned from the Discord APIs and proper constructors to transform the API responses
   into the corresponding records"
  (:require [discord.config :as config]
            [clojure.data.json :as json]
            [gniazdo.core :as ws]))

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
  (send-message [this message]))

(defrecord DiscordGateway [url shards handler websocket auth])

(defn build-gateway [gateway-response api-version]
  (let [gateway-map (into {} gateway-response)
        url (format "%s?v=%s&encoding=%s" (:url gateway-map) api-version "json")]
    (map->DiscordGateway (assoc gateway-map :url url))))


;;; Representing a Discord User
(defrecord User [id username roles deaf mute avatar joined discriminator])

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


;;; Message
(defrecord Message [content attachments embeds sent-time channel author user-mentions role-mentions
                    pinned? everyone-mentioned? id])

(defn build-message [message-map]
  (let [user-wrap (fn [user-map] {:user user-map})
        author    (build-user (user-wrap (get-in message-map [:d :author])))
        users     (map (comp build-user user-wrap) (get-in message-map [:d :mentions]))
        roles     (map (comp build-user user-wrap) (get-in message-map [:d :role_mentions]))]
    (map->Message
      {:author                author
       :user-mentions         users
       :role-mentions         roles
       :channel               (get-in message-map [:d :channel_id])
       :everyone-mentioned?   (get-in message-map [:d :mention_everyone])
       :content               (get-in message-map [:d :content])
       :embeds                (get-in message-map [:d :embeds])
       :attachments           (get-in message-map [:d :attachments])
       :pinned?               (get-in message-map [:d :pinned])
       :id                    (get-in message-map [:d :id])})))

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

