(ns discord.types.channel
  (:require
    [clojure.set :refer [rename-keys]]
    [discord.types.user :as user]))

(def channel-types
  "The types of channels that might exist within Discord Guilds.

   See also:https://discord.com/developers/docs/resources/channel#channel-object-channel-types"
  [:guild-text :dm :guild-voice :group-dm :guild-category :guild-news :guild-store
   :guild-news-thread :guild-public-thread :guild-private-thread :guild-stage-voice])

(defn keys->channel-type-ids
  [channel-type-keys]
  (map (fn [ct] (.indexOf channel-types ct)) channel-type-keys))

(def video-quality-modes
  "The quality of streamed video within the channel."
  [:auto :full])

(defrecord Channel
  [id type guild-id position permission-overwrites name topic nsfw? last-message-id
   rate-limit-per-user recipients icon owner-id application-id parent-id last-pin-timestamp
   rtc-region video-quality-mode message-count member-count])

(defn build-channel
  "Converts a map to a channel record, to parse out of some API fields."
  [m]
  (when m
    (-> m
        (rename-keys {:permission_overwrites :permission-overwrites
                      :last_message_id :last-message-id
                      :rate_limit_per_user :rate-limit-per-user
                      :owner_id :owner-id
                      :application_id :application-id
                      :parent_id :parent-id
                      :last_pin_timestamp :last-pin-timestamp
                      :rtc_region :rtc-region
                      :video_quality_mode :video-quality-mode
                      :message_count :message-count
                      :member_count :member-count})
        (update :video-quality-mode video-quality-modes)
        (update :type channel-types)
        (update :recipients (partial map user/build-user))
        (map->Channel))))
