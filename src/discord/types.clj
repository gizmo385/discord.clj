(ns discord.types
  "Types returned from the Discord APIs and proper constructors to transform the API responses
   into the corresponding records"
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


;;; Representing a discord Channel
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
