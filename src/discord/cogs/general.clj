(ns discord.cogs.general
  (:require [discord.bot :as bot]
            [discord.http :as http]
            [discord.utils :as utils]
            [clojure.tools.logging :as log]
            [clojure.string :as s]))

(bot/defcommand say [client message]
  (bot/say (:content message)))

(bot/defcommand botsay [client message]
  (bot/say (:content message))
  (bot/delete message))

(bot/defcommand working [client message]
  (bot/say "https://giphy.com/gifs/9K2nFglCAQClO"))

(bot/defcommand roll [client message]
  (if-let [potential-number (not-empty (:content message))]
    (try
      (let [limit (Integer/parseInt potential-number)]
        (bot/say (format ":game_die: %d :game_die:" (inc (rand-int limit)))))
      (catch NumberFormatException _
        (bot/say "Error: Please supply a number!")))
    (bot/say (format ":game_die: %d :game_die:" (inc (rand-int 100))))))

(bot/defcommand choose [client message]
  (if-let [choices (utils/words (:content message))]
    (bot/say (rand-nth choices))
    (bot/say "Error: Nothing to choose from!")))

(bot/defcommand servers [client message]
  (let [bot-servers (http/get-servers client)]
    (->> client
         (http/get-servers)
         (map :name)
         (s/join \newline)
         (format "Servers I'm a part of: \n%s")
         (bot/say))))
