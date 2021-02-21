(ns discord.extensions.general
  (:require [clojure.string :as s]
            [discord.bot :as bot]
            [discord.embeds :as e]
            [discord.http :as http]
            [discord.utils :as utils]))

(defn rock-paper-scissors
  [user-choice]
  (if (some #{user-choice} [:rock :paper :scissors])
    (condp = [user-choice (rand-nth [:rock :paper :scissors])]
      ;; Tie choices
      [:rock :rock]           ":moyai: We're even! :moyai:"
      [:paper :paper]         ":newspaper: We're even! :newspaper:"
      [:scissors :scissors]   ":scissors: We're even! :scissors:"

      [:rock :paper]          ":newspaper: I win! :newspaper:"
      [:rock :scissors]       ":scissors: You win! :scissors:"

      [:paper :rock]          ":moyai: You win! :moyai:"
      [:paper :scissors]      ":scissors: I win! :scissors:"

      [:scissors :rock]       ":moyai: I win! :moyai:"
      [:scissors :paper]      ":newspaper: You win! :newspaper:")
    "Please choose either rock, paper, or scissors."))

(bot/install-modules!
  (bot/command
    :say [client message & _]
    (bot/reply-in-channel client message (:content message)))

  (bot/command
    :botsay [client message & _]
    (bot/reply-in-channel client message (:content message))
    (bot/delete-original-message client message))

  (bot/command
    :working [client message]
    (bot/reply-in-channel client message "https://giphy.com/gifs/9K2nFglCAQClO"))

  (bot/command
    :roll
    ([client message]
     (bot/reply-in-channel client message (format ":game_die: %d :game_die:" (inc (rand-int 100)))))
    ([client message limit]
     (try (let [limit-num (Integer/parseInt limit)]
            (bot/reply-in-channel client message (format ":game_die: %d :game_die:"
                                                         (inc (rand-int limit-num))))) 
          (catch Exception e
            (bot/reply-in-channel client message "Please supply a number :)")))))

  (bot/command
    :choose [client message & choices]
    (if (not-empty choices)
      (bot/reply-in-channel client message (rand-nth choices))
      (bot/reply-in-channel client message "Error: Nothing to choose from")))

  (bot/command
    :servers [client message]
    (let [bot-servers (http/get-servers client)]
      (->> bot-servers
           (map :name)
           (s/join \newline)
           (format "Servers I'm a part of: \n%s")
           (bot/reply-in-channel client message))))

  (bot/command
    :flip [client message]
    (let [res (rand-nth ["Heads" "Tails"])]
      (bot/reply-in-channel client message (format "Flipping a coin....%s" res))))

  (bot/command
    :rps [client message user-choice]
    (bot/reply-in-channel client message (rock-paper-scissors (keyword user-choice))))

  (bot/command
    :count [client message number]
    (try
      (let [count-limit (Integer/parseInt number)]
        (doseq [n (range count-limit)]
          (bot/reply-in-channel client message (format "Number: %d" n))))
      (catch NumberFormatException _
        (bot/reply-in-channel client message "Error: Please supply a number!")))
      (doseq [n (range 10)]
        (bot/reply-in-channel client message (format "Number: %d" n)))))
