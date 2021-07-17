(ns discord.types.role
  (:require
    [clojure.set :refer [rename-keys]]
    [discord.types.snowflake :as sf]
    [discord.types.permissions :as perms]))

(defrecord RoleTags
  [bot-id integration-id premium-subscriber-role?])

(defn build-role-tags
  [m]
  (-> m
      (rename-keys {:premium_subscriber :premium-subscriber-role?
                    :integration_id :integration-id
                    :bot_id :bot-id})
      (update :bot-id sf/build-snowflake)
      (update :integration-id sf/build-snowflake)))

(defrecord Role
  [id name color hoist? position permissions managed? mentionable? tags])

(defn build-role
  [m]
  (-> m
      (rename-keys {:hoist :hoist?
                    :managed :managed?
                    :mentionable :mentionable?})
      (update :id sf/build-snowflake)
      (update :permissions #(Integer/parseInt %))
      (update :tags (partial map build-role-tags))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helper functions for determing if a role has a particular permission
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn role-has-permission?
  "Determines whether or not a role has the specified permission."
  [role permission]
  (perms/has-permission? (:permissions role) permission))

(defn role-has-all-permissions?
  "Determines whether or not a role has ALL of the specified permission."
  [role permissions]
  (perms/has-all-permissions? (:permissions role) permissions))
