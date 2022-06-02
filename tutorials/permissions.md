# Permissions

While bots make the moderation of Discord servers easier and allow for easy access to otherwise
cumbersome server maintenance tasks, they also present a problem. To prevent your server from
slipping into chaos, you need to be able to check the it's necessary to check the permissions of
users invoking the commands. Doing this manually would be cumbersome and annoying, so discord.clj
allows you to take a particular command or extension with permission requirements:

```Clojure
(ns cool-extension
  (:requires [discord.bot :as bot]
             [discord.http :as http]
             [discord.types :as types]
             [discord.permissions :as perm])

(bot/defextension voice [client message]
  "Commands for common server adminstrative tasks."
  (:region
    "Returns the current voice region for the guild."
    (let [guild-id      (get-in message [:channel :guild-id])
          guild         (http/get-guild client guild-id)
          voice-region  (-> guild :region name)]
      (bot/say (format "The guild's voice region is currently \"%s\"" voice-region))))

  (:regionlist
    "Lists all supported voice regions."
    (bot/say (format "Supported voice regions: %s" (s/join ", " (keys types/server-region)))))

  (:regionmove
    "Moves the voice region for the guild to a new location."
    {:requires [perm/MANAGE-GUILD]}
    (let [desired-region (->> message :content utils/words rest (s/join " "))
          guild-id (get-in message [:channel :guild-id])]
      (if-let [region-keyword (get types/server-region desired-region)]
        (do
          (bot/say (format "Moving voice server to \"%s\"" desired-region))
          (http/modify-server client guild-id :region desired-region))
        (bot/say (format "The region \"%s\" does not exist."))))))
```

Someone who isn't authorized to actually move the server's voice region (which requires the
`MANAGE_GUILD` permission) would be able to list the available regions and check the current voice
region, but would be unable to tell the bot to move the region.

![Permission Failure](/resources/images/permissions-failure.png)

Compare that to someone who does have the necessary permissions:

![Permission Success](/resources/images/permissions-success.png)
