(ns discord.types.permissions
  "Describes the permissions that a user might have within the Discord API and includes convenience
   functions for determining if a user has those permissions.")

(def permission-values
  "These are the permissions and bitwise permission flags as documented in the Discord developer
   docs, available here: https://discord.com/developers/docs/topics/permissions"
  {:create-instant-invite	0x0000000001
   :kick-members          0x0000000002
   :ban-members           0x0000000004
   :administrator 	      0x0000000008
   :manage-channels 	    0x0000000010
   :manage-guild 	        0x0000000020
   :add-reactions	        0x0000000040
   :view-audit-log	      0x0000000080
   :priority-speaker	    0x0000000100
   :stream	              0x0000000200
   :view-channel	        0x0000000400
   :send-messages	        0x0000000800
   :send-tts-messages	    0x0000001000
   :manage-messages 	    0x0000002000
   :embed-links	          0x0000004000
   :attach-files	        0x0000008000
   :read-message-history	0x0000010000
   :mention-everyone	    0x0000020000
   :use-external-emojis	  0x0000040000
   :view-guild-insights	  0x0000080000
   :connect	              0x0000100000
   :speak	                0x0000200000
   :mute-members	        0x0000400000
   :deafen-members	      0x0000800000
   :move-members	        0x0001000000
   :use-vad	              0x0002000000
   :change-nickname	      0x0004000000
   :manage-nicknames	    0x0008000000
   :manage-roles          0x0010000000
   :manage-webhooks       0x0020000000
   :manage-emojis         0x0040000000
   :use-slash-commands    0x0080000000
   :request-to-speak      0x0100000000
   :manage-threads        0x0400000000
   :use-public-threads    0x0800000000
   :use-private-threads	  0x1000000000})

(defn has-permission?
  "Given an integer representing the permissions held by some entity within Discord and a declared
   permission, determines if the declared permission is included in the held permissions"
  [held-permissions permission-to-check]
  (if-let [permission-value (get permission-values permission-to-check)]
    (-> held-permissions (bit-and permission-value) (= permission-value))
    (throw (ex-info "Permission does not exist!" {:permission permission-to-check}))))

(defn has-all-permissions?
  "Given an integer representing the permissions held by some entity within Discord and a declared
   permission, determines if ALL the declared permissions are included in the held permissions"
  [held-permissions permissions-to-check]
  (every? (partial has-permission? held-permissions) permissions-to-check))
