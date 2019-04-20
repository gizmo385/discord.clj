# Bot Configuration

Below you'll find information about how to perform the basic setup and configuration of discord.clj
so that you can start interacting with it on your servers!

The [core](/src/discord/core.clj) namespace in
discord.clj contains all of the basic code that you need in order to get the Discord bot running:

```Clojure

(ns discord.core
  (:require [discord.bot :as bot])
  (:gen-class))


(defn -main
  "Starts a Discord bot."
  [& args]
  (bot/start))
```

Beyond this simple code snippet, you'll also need to add a `settings.json` file to the
`data/settings` directory. A template of that file [is
available](/data/settings/settings.json.template):

```json
{
    "token" : "Your Auth Token",
    "prefix" : "!",
    "bot-name" : "CoolDiscordBot",
    "extension-folders" : [
        "src/discord/extensions"
    ]
}
```


| Configuration Field | Description |
|---|---|
| token | This is the token for the bot you have created in the Discord developer portal †. |
| prefix | The string that will cause the bot to check trigger commands and extensions. |
| bot-name | The name of the bot. |
| extension-folders | The folders from which the bot will load Clojure files searching for extensions. |

† [The Discord Developer Portal](https://discordapp.com/developers/applications).

Once you have started the bot and added it to your server, you're ready to begin creating your own
commands extensions!
