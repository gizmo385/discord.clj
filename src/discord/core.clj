(ns discord.core
  (:require [discord.config :as config]
            [discord.types :refer [Authenticated] :as types]
            [discord.gateway :as gw])
  (:import [discord.types DiscordGateway])
  (:gen-class))

(extend-type DiscordGateway
  Authenticated
  (token [this]
    (.token (:auth this)))
  (token-type [this]
    (.token-type (:auth this))))

(defn -main
  "Spins up a new bot and reads messages from it"
  [& args]
  (let [auth]
    (let [gateway (gw/connect-to-gateway auth prn)]
      (gw/send-identify gateway)
      (prn gateway)
      (try
        (while true
          (Thread/sleep 3000))
        (finally (.close gateway))))))

(comment
  (gw/connect-to-gateway (DiscordBotClient.))
  )
