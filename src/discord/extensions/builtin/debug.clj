(ns discord.extensions.builtin.debug
  (:require
    [clojure.pprint :refer [pprint]]
    [discord.api.guilds :as guilds-api]
    [discord.interactions.core :as i]
    [discord.interactions.slash :as slash]
    [discord.extensions.core :as ext]
    [discord.extensions.utils :as ext-utils]
    [discord.types.permissions :as perms]
    [discord.types.role :as roles]
    [discord.embeds :as e]
    ))

(slash/register-globally-on-startup!
  (slash/command
    :debug "Debugging information"
    (slash/sub-command :get-my-permissions "Retrieve your permissions")
    (slash/sub-command
      :get-permissions "Retrieve a different user's permissions"
      (slash/user-option :user "The user whose permissions you want to check." :required true))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Debugging user permissions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn determine-user-permissions
  "Given a list of roles that a user has and a list of roles available in the guild, determines
   which permissions a user has been granted based on their roles."
  [user-roles guild-roles]
  (->> (for [role user-roles]
         (map (fn [permission] [permission (roles/role-has-permission? role permission)])
              (keys perms/permission-values)))
       (map (partial into {}))
       (apply merge-with #(or %1 %2))))

(defn permission-map->embed
  [permission-map]
  (reduce (fn [embed [permission possessed?]]
            (e/+field embed (name permission) (str possessed?) :inline true))
          (e/create-embed)
          permission-map))

(defn build-user-permissions-embed
  [user-id user-role-ids guild-roles]
  (let [user-roles (filter (fn [role] (some #{(:id role)} user-role-ids)) guild-roles)
        user-permissions (determine-user-permissions user-roles guild-roles)]
    (permission-map->embed user-permissions)))

(defmethod slash/handle-slash-command-interaction [:debug :get-permissions]
  [{:keys [interaction arguments]} auth metadata]
  (if-let [guild-id (:guild-id interaction)]
    (let [user-id (:user arguments)
          _ (println :user-id user-id)
          guild-member (guilds-api/get-guild-member auth guild-id user-id)
          user-role-ids (:role-ids guild-member)
          guild-roles (guilds-api/get-guild-roles auth (:guild-id interaction))
          permissions-embed (build-user-permissions-embed user-id user-role-ids guild-roles)
          message (format "Permissions for <@%s>:" user-id)]
      (println :message message)
      (println :permissions-embed permissions-embed)
      (i/channel-message-response interaction auth message nil [permissions-embed]))
    (i/channel-message-response interaction auth "Please run this command within a guild!" nil nil)))

(defmethod slash/handle-slash-command-interaction [:debug :get-my-permissions]
  [{:keys [interaction]} auth metadata]
  (if-let [user-role-ids (get-in interaction [:member :role-ids])]
    (let [user-id (get-in interaction [:member :user :id])
          guild-roles (guilds-api/get-guild-roles auth (:guild-id interaction))
          permissions-embed (build-user-permissions-embed user-id user-role-ids guild-roles)
          message (format "Permissions for <@%s>:" user-id)]
      (i/channel-message-response interaction auth message nil [permissions-embed]))
    (i/channel-message-response interaction auth "Please run this command within a guild!" nil nil)))
