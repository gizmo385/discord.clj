(ns discord.cogs.general
  (:require [discord.bot :as bot]))

(bot/defcommand say [client message]
  (bot/say (:content message)))

(bot/defcommand botsay [client message]
  (bot/say (:content message))
  (bot/delete message))

(bot/defcommand working [client message]
  (bot/say "https://giphy.com/gifs/9K2nFglCAQClO"))
