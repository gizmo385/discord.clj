(ns discord.extensions.general
  (:require
    [clojure.string :as s]
    [discord.bot :as bot]
    [discord.interactions.components :as c]
    [discord.interactions.core :as i]
    [discord.interactions.slash :as slash]
    [discord.embeds :as e]
    [discord.http :as http]
    [discord.utils :as utils]
    [discord.types :as types]
    [discord.types.guild :as guild]))

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

(bot/install-prefix-commands!
  (bot/prefix-command
    :say [client message & _]
    (let [space-index (s/index-of (:content message) " ")]
      (if (some? space-index)
        (bot/reply-in-channel client message (subs (:content message) space-index))
        (bot/reply-in-channel
          client message "https://media2.giphy.com/media/L1W47cPwMyrUs7zEjP/giphy.gif"))))

  (bot/prefix-command
    :botsay [client message & _]
    (let [space-index (s/index-of (:content message) " ")]
      (bot/delete-original-message client message)
      (when (some? space-index)
        (bot/reply-in-channel client message (subs (:content message) space-index)))))

  (bot/prefix-command
    :working [client message]
    (bot/reply-in-channel client message "https://giphy.com/gifs/9K2nFglCAQClO"))

  (bot/prefix-command
    :roll
    ([client message]
     (bot/reply-in-channel client message (format ":game_die: %d :game_die:" (inc (rand-int 100)))))
    ([client message limit]
     (try (let [limit-num (Integer/parseInt limit)]
            (bot/reply-in-channel client message (format ":game_die: %d :game_die:"
                                                         (inc (rand-int limit-num))))) 
          (catch Exception e
            (bot/reply-in-channel client message "Please supply a number :)")))))

  (bot/prefix-command
    :choose [client message & choices]
    (if (not-empty choices)
      (bot/reply-in-channel client message (rand-nth choices))
      (bot/reply-in-channel client message "Error: Nothing to choose from")))

  (bot/prefix-command
    :servers [client message]
    (let [bot-servers (http/get-servers client)]
      (->> bot-servers
           (map :name)
           (s/join \newline)
           (format "Servers I'm a part of: \n%s")
           (bot/reply-in-channel client message))))

  (bot/prefix-command
    :flip [client message]
    (let [res (rand-nth ["Heads" "Tails"])]
      (bot/reply-in-channel client message (format "Flipping a coin....%s" res))))

  (bot/prefix-command
    :rps [client message user-choice]
    (bot/reply-in-channel client message (rock-paper-scissors (keyword user-choice))))

  (bot/prefix-command
    :count [client message number]
    (try
      (let [count-limit (Integer/parseInt number)]
        (doseq [n (range count-limit)]
          (bot/reply-in-channel client message (format "Number: %d" n))))
      (catch NumberFormatException _
        (bot/reply-in-channel client message "Error: Please supply a number!"))))

  (bot/prefix-command
    :get-guild
    [client message]
    (let [raw-guild (http/get-guild client 328324837963464705)]
      (clojure.pprint/pprint raw-guild)
      (clojure.pprint/pprint (guild/build-guild raw-guild)))))

(slash/register-globally-on-startup!
  (slash/command
    :echo "Echo back text"
    (slash/string-option :text "The text to echo" :required? true)))

(defmethod slash/handle-slash-command-interaction [:echo]
  [{:keys [interaction arguments]} gateway]
  (i/channel-message-response interaction gateway (:text arguments) nil nil))
