# Message Embeds

Message embeds are a feature in Discord that allows you to send messages which feature a much more
complicated structure than a normal text message. They are documented in the discord developer
documentation [here](https://discordapp.com/developers/docs/resources/channel#embed-object).

Discord.clj allows you to build embeds using the `discord.embeds` namespace defined
[here](https://github.com/gizmo385/discord.clj/blob/develop/docs/embeds.md). Here is how you might
define an embed message:

```Clojure
(ns my-cool-extension
 (:require [discord.bot :as bot]
           [discord.utils :as utils
           [discord.embeds :as embeds])

(bot/defcommand embedtest
  [client message]
  (bot/say (-> (embeds/create-embed :title "Testing"
                                    :color (utils/rgb->integer 0 255 255)
                                    :description "jfdkajfldkakj")
               (embeds/+field "KeyA" "KeyA")
               (embeds/+field "KeyB" "KeyB")
               (embeds/+field "KeyC" "KeyC")
               (embeds/+footer "Test footer"
                               :icon-url "https://www.pngkit.com/png/detail/17-179788_discord-logo-01-discord-logo-png.png")
               (embeds/+thumbnail
                 :url "https://www.pngkit.com/png/detail/17-179788_discord-logo-01-discord-logo-png.png")
               (embeds/+image
                 :url "https://www.gstatic.com/tv/thumb/v22episodes/10443766/p10443766_e_v8_ab.jpg")
               (embeds/+author :name "Rick & Morty"))))
```

This should result in something like the below image:

![Embed Test](resources/images/embedtest.png)
