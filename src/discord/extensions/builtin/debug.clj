(ns discord.extensions.builtin.debug
  (:require
    [clojure.pprint :refer [pprint]]
    [discord.api.guilds :as guilds-api]
    [discord.interactions.core :as i]
    [discord.interactions.slash :as slash]
    [discord.extensions.core :as ext]
    [discord.extensions.utils :as ext-utils]
    [discord.types.permissions :as perms]
    ))

(slash/register-globally-on-startup!
  (slash/command
    :debug "Debugging information"
    (slash/sub-command :get-permissions "Retrieve user permissions")))

(defmethod slash/handle-slash-command-interaction [:debug :get-permissions]
  [{:keys [interaction]} auth metadata]
  (if-let [guild-id (:guild-id interaction)]
    (let [user-id (get-in interaction [:user :id])
          guild-member (guilds-api/get-guild-member auth guild-id user-id)
          user-role-ids (:role-ids guild-member)
          roles (guilds-api/get-guild-roles auth guild-id)
          user-roles (filter (fn [role] (some #{(:id role)} user-role-ids)) roles)]
      (println "User Roles")
      (pprint user-roles))
    (i/channel-message-response interaction auth "Please run this command within a guild!" nil nil)))
