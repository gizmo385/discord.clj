(ns discord.types.guild
  (:require
    [clojure.set :refer [rename-keys]]
    [discord.types.channel :as channel]
    [discord.types.snowflake :as sf]
    [discord.types.user :as user]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Guild members
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord GuildMember
  [user nickname roles joined-at premium-since deaf mute pending permissions])

(defn build-guild-member
  [m]
  (-> m
      (rename-keys {:joined_at :joined-at
                    :premium_since :premium-since})
      (update :user user/build-user)
      (map->GuildMember)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Guild enum definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def default-message-notification-level
  [:all-messages :only-mentions])

(def explicit-content-filter-levels
  [:disabled :members-without-roles :all-members])

(def mfa-levels
  [:none :elevated])

(def verification-levels
  [:none :low :medium :high :very-high])

(def nsfw-levels
  [:default :explicit :safe :age-restricted])

(def premium-tiers
  [:none :tier-1 :tier-2 :tier-3])

(defrecord Guild
  [id name description owner-id region verification-level default-message-notifications
   explicit-content-filter roles features mfa-level application-id large? member-count members
   channels threads max-members premium-tier premium-subscription-count preferred-locale nsfw-level])

(defn build-guild
  "Converts a map to a guild record, to parse out of some API fields."
  [m]
  (println (:premium_tier m) (type (:premium_tier m)))
  (-> m
      (rename-keys {:owner_id :owner-id
                    :verification_level :verification-level
                    :default_message_notifications :default-message-notifications
                    :explicit_content_filter :explicit-content-filter
                    :mfa_level :mfa-level
                    :nsfw_level :nsfw-level
                    :application_id :application-id
                    :member_count :member-count
                    :max_members :max-members
                    :premium_tier :premium-tier
                    :premium_subscription_count :premium-subscription-count
                    :preferred_locale :preferred-locale})
      (update :id sf/build-snowflake)
      (update :owner-id sf/build-snowflake)
      (update :premium-tier premium-tiers)
      (update :explicit-content-filter explicit-content-filter-levels)
      (update :verification-level verification-levels)
      (update :nsfw-level nsfw-levels)
      (update :members (partial map build-guild-member))
      (update :channels (partial map channel/build-channel))
      (update :threads (partial map channel/build-channel))
      (map->Guild)))
