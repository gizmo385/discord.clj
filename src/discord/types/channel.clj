(ns discord.types.channel
  (:require
    [discord.types.user :as user]
    [discord.types.protocols :as proto]))

(def channel-types
  "The types of channels that might exist within Discord Guilds.

   See also:https://discord.com/developers/docs/resources/channel#channel-object-channel-types"
  [:guild-text :dm :guild-voice :group-dm :guild-category :guild-news :guild-store
   :guild-news-thread :guild-public-thread :guild-private-thread :guild-stage-voice])

(def video-quality-modes
  "The quality of streamed video within the channel."
  [:auto :full])

(defrecord Channel
  [id type guild-id position permission-overwrites name topic nsfw? last-message-id
   rate-limit-per-user recipients icon owner-id application-id parent-id last-pin-timestamp
   rtc-region video-quality-mode message-count member-count]
  proto/Snowflake
  (->snowflake [c] (:id c)))

(defn build-channel
  "Converts a map to a channel record, to parse out of some API fields."
  [m]
  (->> m
       (update :video-quality-mode video-quality-modes)
       (update :type channel-types)
       (update :recipients (partial map user/build-user))
       (map->Channel)))
