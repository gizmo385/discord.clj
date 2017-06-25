(ns discord.http
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]))

(defonce user-agent "DiscordBot (https://github.com/gizmo385/discord.clj)")
(defonce discord-url "https://discordapp.com/api/v6")

(defprotocol Authenticated
  (token [this])
  (token-type [this]))

(defn discord-request [auth method route & data]
  (let [headers {:User-Agent    user-agent
                 :Authorization (str (token-type auth) (token auth))}
        url (str discord-url route)
        request (assoc data :headers headers :url url :method method)]
    (client/request request)))

(comment

  )
