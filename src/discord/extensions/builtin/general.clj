(ns discord.extensions.builtin.general
  (:require
    [clojure.string :as s]
    [discord.extensions.core :as ext]
    [discord.extensions.utils :as ext-utils]
    [discord.interactions.components :as c]
    [discord.interactions.core :as i]
    [discord.interactions.slash :as slash]
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

(ext/install-prefix-commands!
  (ext/prefix-command
    :say [gateway message & _]
    (let [space-index (s/index-of (:content message) " ")]
      (if (some? space-index)
        (ext-utils/reply-in-channel gateway message (subs (:content message) space-index))
        (ext-utils/reply-in-channel
          gateway message "https://media2.giphy.com/media/L1W47cPwMyrUs7zEjP/giphy.gif"))))

  (ext/prefix-command
    :botsay [gateway message & _]
    (let [space-index (s/index-of (:content message) " ")]
      (ext-utils/delete-original-message gateway message)
      (when (some? space-index)
        (ext-utils/reply-in-channel gateway message (subs (:content message) space-index)))))

  (ext/prefix-command
    :working [gateway message]
    (println "Inside the working command")
    (ext-utils/reply-in-channel gateway message "https://giphy.com/gifs/9K2nFglCAQClO"))

  (ext/prefix-command
    :roll
    ([gateway message]
     (ext-utils/reply-in-channel gateway message (format ":game_die: %d :game_die:" (inc (rand-int 100)))))
    ([gateway message limit]
     (try (let [limit-num (Integer/parseInt limit)]
            (ext-utils/reply-in-channel gateway message (format ":game_die: %d :game_die:"
                                                         (inc (rand-int limit-num))))) 
          (catch Exception e
            (ext-utils/reply-in-channel gateway message "Please supply a number :)")))))

  (ext/prefix-command
    :choose [gateway message & choices]
    (if (not-empty choices)
      (ext-utils/reply-in-channel gateway message (rand-nth choices))
      (ext-utils/reply-in-channel gateway message "Error: Nothing to choose from")))

  (ext/prefix-command
    :servers [gateway message]
    (let [bot-servers (http/get-servers gateway)]
      (->> bot-servers
           (map :name)
           (s/join \newline)
           (format "Servers I'm a part of: \n%s")
           (ext-utils/reply-in-channel gateway message))))

  (ext/prefix-command
    :flip [gateway message]
    (let [res (rand-nth ["Heads" "Tails"])]
      (ext-utils/reply-in-channel gateway message (format "Flipping a coin....%s" res))))

  (ext/prefix-command
    :rps [gateway message user-choice]
    (ext-utils/reply-in-channel gateway message (rock-paper-scissors (keyword user-choice))))

  (ext/prefix-command
    :count [gateway message number]
    (try
      (let [count-limit (Integer/parseInt number)]
        (doseq [n (range count-limit)]
          (ext-utils/reply-in-channel gateway message (format "Number: %d" n))))
      (catch NumberFormatException _
        (ext-utils/reply-in-channel gateway message "Error: Please supply a number!"))))

  (ext/prefix-command
    :get-guild
    [gateway message]
    (let [raw-guild (http/get-guild gateway 328324837963464705)]
      (clojure.pprint/pprint raw-guild)
      (clojure.pprint/pprint (guild/build-guild raw-guild)))))

(slash/register-globally-on-startup!
  (slash/command
    :echo "Echo back text"
    (slash/string-option :text "The text to echo" :required? true)))

(defmethod slash/handle-slash-command-interaction [:echo]
  [{:keys [interaction arguments]} gateway]
  (i/channel-message-response interaction gateway (:text arguments) nil nil))
