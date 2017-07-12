# Using discord.clj

Currently there are 2 ways of defining bots in discord.clj:

## Using cog/ folders

You can break your cogs up into different folders and then have discord.clj load those by using the
`with-file-cogs` macro in the `discord.bot` namespace.

Example:

###### /path/to/cogs/admin.clj:
```Clojure
(ns path.to.cogs.admin
  (:require [discord.bot :as bot]
            [discord.http :as http]
            [discord.utils :as utils]))

(bot/defcog admin [client message]
  ;; Kick the users mentioned in the message
  (:kick
    (doseq [user (:user-mentions message)]
      (let [user-id (:id user)
            guild-id (get-in message [:channel :guild-id] message)]
        (http/kick client guild-id user-id))))

  ;; Send a message to all servers
  (:broadcast
    (let [bcast-message (->> message :content utils/words rest (s/join " "))
          servers (http/get-servers client)]
      (doseq [server servers]
        (http/send-message client server bcast-message)))))
```

###### /path/to/cogs/general.clj:
```Clojure
(ns path.to.cogs.admin
  (:require [discord.bot :as bot]))

;;; The defcommand macro is great for one-off functions you wish to expose on the bot
(bot/defcommand botsay [client message]
  (bot/say (:content message))
  (bot/delete message))

(bot/defcommand working [client message]
  (bot/say "https://giphy.com/gifs/9K2nFglCAQClO"))
```

###### data/settings/settings.json:
```javascript
{
    "token" : "auth-token",
    "prefix" : "^",
    "bot-name" : "bot-name",
    "cog-folders" : [
        "path/to/cogs"
    ]
}
```

###### my_cool_bot/src/core.clj:
```Clojure
(ns my-cool-bot.core
  (:require [discord.bot :as bot]
            [discord.config :as config]))
(defn -main
  "Creates a new discord bot and supplies a series of extensions to it."
  [& args]
  (let [bot-name    (config/get-bot-name)
        prefix      (config/get-prefix)
        cog-folders (config/get-cog-folders)]
    (bot/with-file-cogs bot-name prefix cog-folders)))
```
---
Running your bot now would result in logs similar to below:
```
2017-07-12 00:50:04.037:INFO::main: Logging initialized @4920ms
Jul 12, 2017 12:50:05 AM discord.bot invoke
INFO: Loading cogs from: /path/to/cogs/admin.clj
Jul 12, 2017 12:50:05 AM discord.core invoke
INFO: Loaded 1 cogs: admin
Jul 12, 2017 12:50:06 AM discord.gateway invoke
INFO: Connected to Discord Gateway
Jul 12, 2017 12:50:06 AM discord.gateway invoke
INFO: Setting heartbeat interval to 41250 milliseconds
Jul 12, 2017 12:50:06 AM discord.bot invoke
INFO: Creating bot with prefix: ^
```

## Defining Inline

The alternative to splitting up your defintions is to define things inline using the
`open-with-cogs` macro defined in the `discord.bot` namespace. Below is an equivalent to the bot
shown above:

###### my_cool_bot/src/core.clj:

```Clojure
(ns discord.core
  (:require [clojure.string :as s]
            [discord.bot :refer [say delete] :as bot]
            [discord.utils :as utils]
            [discord.http :as http]))

;;; The admin cog
(bot/defcog admin-cog [client message]
  (:kick
    (doseq [user (:user-mentions message)]
      (let [user-id (:id user)
            guild-id (get-in message [:channel :guild-id] message)]
        (http/kick client guild-id user-id))))

  (:broadcast
    (let [bcast-message (->> message :content utils/words rest (s/join " "))
          servers (http/get-servers client)]
      (doseq [server servers]
        (http/send-message client server bcast-message)))))


(defn -main
  "Creates a new discord bot and supplies a series of extensions to it."
  [& args]
  (bot/open-with-cogs
    "TestDiscordBot" "^"
    :botsay (fn [client message]
              (say (:content message))
              (delete message))
    :working (fn [_ _]
               (say "https://giphy.com/gifs/9K2nFglCAQClO"))
    :admin  admin-cog))
```

For a simple bot or for quick experimentation, this method is a lot quicker for creating new bots,
but it is also less extensible and grows quickly in comparison to the method described above.
