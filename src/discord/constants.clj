(ns discord.constants)

(defonce api-version 6)
(defonce user-agent "discord.clj (https://github.com/gizmo385/discord.clj)")
(defonce discord-url (format "https://discordapp.com/api/v%s" api-version))

;; Units of time
(defonce SECONDS-IN-MINUTE 60)
(defonce SECONDS-IN-HOUR (* 60 SECONDS-IN-MINUTE))
(defonce SECONDS-IN-DAY (* 24 SECONDS-IN-HOUR))

(defonce MILLISECONDS-IN-SECOND 1000)
(defonce MILLISECONDS-IN-MINUTE (* MILLISECONDS-IN-SECOND SECONDS-IN-MINUTE))
(defonce MILLISECONDS-IN-HOUR (* MILLISECONDS-IN-SECOND SECONDS-IN-HOUR))
