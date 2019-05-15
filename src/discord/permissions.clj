(ns discord.permissions
  (:require [discord.types :as types]
            [discord.http :as http]))

(defrecord Role [id name permissions])

(defn- build-roles [roles]
  (for [role roles]
    (map->Role
      {:id          (:id role)
       :name        (:name role)
       :permissions (:permissions role)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Caching roles that are returned from the Discord APIs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce roles-cached? (atom {}))
(defonce role-cache (atom {}))

(defn clear-role-cache! [guild-id]
  (let [current-value @role-cache]
    (assoc current-value guild-id {})
    (reset! role-cache {})))

(defn get-roles! [auth guild-id]
  (if (get @roles-cached? guild-id false)
    (get @role-cache guild-id)
    (let [roles (build-roles (http/get-roles auth guild-id))
          current-value @role-cache]
      (swap! role-cache assoc guild-id roles)
      (swap! roles-cached? assoc guild-id true)
      roles)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Permission Handling
;;;
;;; These permissions are defined in the Discord API documentation at the URL below:
;;;   https://discordapp.com/developers/docs/topics/permissions
;;;
;;; Permissions are defined using bitwise hexadecimal integers. This allows for these permissions to
;;; be uniquely combined with bitwise operations without losing visbility into individual
;;; permissions.
;;;
;;; Currently, per-channel permission overwrites are not handled.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord Permission [name flag])

(defonce CREATE-INSTANT-INVITE  (Permission. :create-instant-invite 0x00000001))
(defonce KICK-MEMBERS           (Permission. :kick-members          0x00000002))
(defonce BAN-MEMBERS            (Permission. :ban-members           0x00000004))
(defonce ADMINISTRATOR          (Permission. :administrator         0x00000008))
(defonce MANAGE-CHANNELS        (Permission. :manage-channels       0x00000010))
(defonce MANAGE-GUILD           (Permission. :manage-guild          0x00000020))
(defonce ADD-REACTIONS          (Permission. :add-reactions         0x00000040))
(defonce VIEW-AUDIT-LOG         (Permission. :view-audit-log        0x00000080))
(defonce VIEW-CHANNEL           (Permission. :view-channel          0x00000400))
(defonce SEND-MESSAGES          (Permission. :send-messages         0x00000800))
(defonce SEND-TTS-MESSAGES      (Permission. :send-tts-messages     0x00001000))
(defonce MANAGE-MESSAGES        (Permission. :manage-messages       0x00002000))
(defonce EMBED-LINKS            (Permission. :embed-links           0x00004000))
(defonce ATTACH-FILES           (Permission. :attach-files          0x00008000))
(defonce READ-MESSAGE-HISTORY   (Permission. :read-message-history  0x00010000))
(defonce MENTION-EVERYONE       (Permission. :mention-everyone      0x00020000))
(defonce USE-EXTERNAL-EMOJIS    (Permission. :use-external-emojis   0x00040000))
(defonce CONNECT                (Permission. :connect               0x00100000))
(defonce SPEAK                  (Permission. :speak                 0x00200000))
(defonce MUTE-MEMBERS           (Permission. :mute-members          0x00400000))
(defonce DEAFEN-MEMBERS         (Permission. :deafen-members        0x00800000))
(defonce MOVE-MEMBERS           (Permission. :move-members          0x01000000))
(defonce USE-VAD                (Permission. :use-vad               0x02000000))
(defonce CHANGE-NICKNAME        (Permission. :change-nickname       0x04000000))
(defonce MANAGE-NICKNAMES       (Permission. :manage-nicknames      0x08000000))
(defonce MANAGE-ROLES           (Permission. :manage-roles          0x10000000))
(defonce MANAGE-WEBHOOKS        (Permission. :manage-webhooks       0x20000000))
(defonce MANAGE-EMOJIS          (Permission. :manage-emojis         0x40000000))

(defn compute-user-permissions
  "Given a user, computes the permissions available to that user."
  [auth user guild]
  (let [user-roles  (:roles (http/get-guild-member auth guild user))
        guild-roles (get-roles! auth guild)]
    ;; We're going to bitwise or all of these role permissions to get the user's permissions.
    (apply
      bit-or
      (for [role guild-roles]
        (if (some #{(:id role)} user-roles)
          ;; If the user has the role, then we'll return that role's permissions. If not, we return
          ;; 0 since it will not affect the permission calculations later on.
          (:permissions role)
          0)))))

(defn has-permission?
  "Determines if a user has been granted a particular permission."
  [auth message permission]
  (let [user              (:author message)
        guild             (get-in message [:channel :guild-id])
        user-permissions  (compute-user-permissions auth user guild)
        flag              (:flag permission)]
    (= (bit-and user-permissions flag) flag)))

(defn has-permissions?
  "Determines if a user has every required permission."
  [auth message permissions]
  (every? true? (map (partial has-permission? auth message) permissions)))
