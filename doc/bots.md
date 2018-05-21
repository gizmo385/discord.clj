; Creating Bots Using discord.clj

Currently there are 2 ways of defining bots in discord.clj:

## Using extension/ folders

You can break your extensions up into different folders and then have discord.clj load those by using the
`with-file-extensions` macro in the `discord.bot` namespace.

Example:

###### /path/to/extensions/admin.clj:
```Clojure
(ns path.to.extensions.admin
  (:require [discord.bot :as bot]
            [discord.http :as http]
            [discord.utils :as utils]))

(bot/defextension admin [client message]
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

###### /path/to/extensions/general.clj:
```Clojure
(ns path.to.extensions.admin
  (:require [discord.bot :as bot]))

;;; The defcommand macro is great for one-off functions you wish to expose on the bot
(bot/defcommand botsay [client message]
  (bot/say (:content message))
  (bot/delete message))

(bot/defcommand working [client message]
  (bot/say "https://giphy.com/gifs/9K2nFglCAQClO"))
```

###### data/settings/settings.json:
```json
{
    "token" : "auth-token",
    "prefix" : "^",
    "bot-name" : "bot-name",
    "extension-folders" : [
        "path/to/extensions"
    ]
}
```

For the `extension-folders` configuration variable, it should be noted that any and all clojure files in
those directories will be loaded. Care should be taken when loading extensions and handlers from untrusted
3rd parties.

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
        extension-folders (config/get-extension-folders)]
    (bot/with-file-extensions bot-name prefix extension-folders)))
```
---
Running your bot now would result in logs similar to below:
```
2017-07-12 00:50:04.037:INFO::main: Logging initialized @4920ms
Jul 12, 2017 12:50:05 AM discord.bot invoke
INFO: Loading extensions from: /path/to/extensions/admin.clj
Jul 12, 2017 12:50:05 AM discord.core invoke
INFO: Loaded 1 extensions: admin
Jul 12, 2017 12:50:06 AM discord.gateway invoke
INFO: Connected to Discord Gateway
Jul 12, 2017 12:50:06 AM discord.gateway invoke
INFO: Setting heartbeat interval to 41250 milliseconds
Jul 12, 2017 12:50:06 AM discord.bot invoke
INFO: Creating bot with prefix: ^
```

## Defining Inline

The alternative to splitting up your defintions is to define things inline using the
`open-with-extensions` macro defined in the `discord.bot` namespace. Below is an equivalent to the bot
shown above:

###### my_cool_bot/src/core.clj:

```Clojure
(ns discord.core
  (:require [clojure.string :as s]
            [discord.bot :refer [say delete] :as bot]
            [discord.utils :as utils]
            [discord.http :as http]))

;;; The admin extension
(bot/defextension admin-extension [client message]
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
  (bot/open-with-extensions
    "TestDiscordBot" "^"
    :botsay (fn [client message]
              (say (:content message))
              (delete message))
    :working (fn [_ _]
               (say "https://giphy.com/gifs/9K2nFglCAQClO"))
    :admin  admin-extension))
```

For a simple bot or for quick experimentation, this method is a lot quicker for creating new bots,
but it is also less extensible and grows quickly in comparison to the method described above.

## Custom Message Handlers

For a lot of extension that you might want to supply to your server, simple call and response
mechanisms based on a predefined prefix won't work. In a lot of cases, you want to intercept all
messages making there way to the server and check for something in that message. For the sake of
example, let's say you run a server that is against pancakes and you want to implement something to
prevent your server patrons from talking about pancakes.

To do this, we'll implement a message handler that checks for the presence of "pancake" in server
messages and deletes them:

```Clojure
(ns discord.extensions.no-swearing
  (:require [clojure.core.async :refer [go >!] :as async]
            [clojure.string :refer [starts-with?] :as s]
            [discord.bot :as bot]
            [discord.config :as config]
            [discord.constants :as const]
            [discord.http :as http]
            [discord.utils :as utils]))

;;; Define the list of blocked words
(defonce BLOCKED-WORDS
  ["pancake"])

(defn- check-message
  "Scans message text to determine if any blocked words are used."
  [message-text]
  (let [message (s/lower-case message-text)]
    (some true?
          (for [blocked-word BLOCKED-WORDS]
            (s/includes? message blocked-word)))))

(bot/defhandler no-pancakes-handler [prefix client message]
  (let [message-content (:content message)
        message-channel (:channel message)
        send-channel (:send-channel client)
        needs-deletion? (check-message message-content)]
    (if needs-deletion?
      (do
        (http/delete-message client message-channel message)
        (go (>! send-channel {:channel message-channel
                              :content "The use of :pancakes: in our server is strictly prohibited!"
                              :options {}}))))))
```
This custom handler is pretty straightforward. For every message that the bot receives, it is
checking to see if the word "pancake" is in the message. If it is, then it will delete the message
and send that user a warning saying that they should behave themselves.

A more complicated handler implementation can be seen in the implementation of the 'alias'
functionality, which is defined in
[this extension file](https://github.com/gizmo385/discord.clj/blob/master/src/discord/extensions/alias.clj).
This only does this handler looks for aliases that have been pre-registered and the sends the
command that the alias corresponds to back to the client's receive channel. Once in the receive
channel, it will be treated like any other message received by the bot and travel through the same
message pipeline.

A custom message handler takes 3 arguments:
1. The prefix that is being used for passing messages to extensions.
2. The client being used to communicate with Discord.
3. The message that was received.

Once a custom message handler is defined, any normal text messages sent to text channels that the
bot is a part of will be additionally sent through the message handler.
