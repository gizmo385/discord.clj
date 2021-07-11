(ns discord.types.auth
  (:require
    [integrant.core :as ig]))

(defprotocol Authenticated
  (token [this])
  (token-type [this]))

(defrecord BotToken [auth-token]
  Authenticated
  (token [_] auth-token)
  (token-type [_] "Bot"))

(defmethod ig/init-key :discord/auth
  [_ {:keys [config]}]
  (-> config :token ->BotToken))
