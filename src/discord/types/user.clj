(ns discord.types.user
  (:require
    [clojure.set :refer [rename-keys]]
    [discord.utils :as utils]
    [discord.types.snowflake :as sf]))

(def premium-types
  "The types of premium Discord subscriptions that can be seen on a user.

   See also: https://discord.com/developers/docs/resources/user#user-object-premium-types"
  [:none :nitro-classic :nitro])

(def user-flags
  "Potential flags on a user's account.

   See also: https://discord.com/developers/docs/resources/user#user-object-user-flags"
  [:none :discord-employee :partnered-server-owner :hypesquad-events :bug-hunter-level-1
   :house-bravery :house-brilliance :house-balance :early-supporter :team-user :bug-hunter-level-2
   :verified-bot :early-verified-bot-developer :discord-certified-moderator])

(defrecord User
  [id username discriminator avatar bot? system? mfa-enabled? locale email flags premium-type
   public-flags])

(defn build-user
  "Converts a map to a channel record, to parse out of some API fields."
  [m]
  (-> m
      (rename-keys {:premium_type :premium-type
                    :public_flags :public-flags
                    :mfa_enabled :mfa-enabled?
                    :bot :bot?
                    :system :system?})
      (update :id sf/build-snowflake)
      (update :premium-type (partial utils/index-of premium-types))
      (map->User)))
