(ns discord.api.interactions
  (:require
    [discord.api.base :as api]))

(defn respond-to-interaction
  [auth interaction response]
  (let [endpoint (format "/interactions/%s/%s/callback" (:id interaction) (:token interaction))]
    (api/discord-request auth endpoint :post :json response)))

(defn bulk-upsert-global-slash-commands
  [auth application-id commands]
  (let [endpoint (format "/applications/%s/commands" application-id)]
    (api/discord-request auth endpoint :put :json commands)))
