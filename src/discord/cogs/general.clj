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


(bot/defcommand flip [client message]
  (bot/say (format "Flipping a coin....%s" (rand-nth ["Heads" "Tails"]))))


(bot/defcommand rps [client message]
  (if-let [user-choice (-> message :content utils/words first keyword)]
    (if (some #{user-choice} [:rock :paper :scissors])
      (let [bot-choice (rand-nth [:rock :paper :scissors])]
        (condp = [user-choice bot-choice]
          ;; Tie choices
          [:rock :rock]           (bot/say ":moyai: We're even! :moyai:")
          [:paper :paper]         (bot/say ":newspaper: We're even! :newspaper:")
          [:scissors :scissors]   (bot/say ":scissors: We're even! :scissors:")

          [:rock :paper]          (bot/say ":newspaper: I win! :newspaper:")
          [:rock :scissors]       (bot/say ":scissors: You win! :scissors:")

          [:paper :rock]          (bot/say ":moyai: You win! :moyai:")
          [:paper :scissors]      (bot/say ":scissors: I win! :scissors:")

          [:scissors :rock]       (bot/say ":moyai: I win! :moyai:")
          [:scissors :paper]      (bot/say ":newspaper: You win! :newspaper:")))
      (bot/say "Please choose either rock, paper, or scissors."))
    (bot/say "Please choose either rock, paper, or scissors.")))
