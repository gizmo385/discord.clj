(ns discord.core
  (:require [clojure.tools.logging :as log]
            [clojure.string :as s]
            [discord.bot :refer [say delete] :as bot]
            [discord.config :as config]
            [discord.utils :as utils]
            [discord.http :as http])
  (:gen-class))

(defn -main
  "Creates a new discord bot and supplies a series of extensions to it."
  [& args]
  (let [bot-name    (config/get-bot-name)
        prefix      (config/get-prefix)
        cog-folders (config/get-cog-folders)]
    (bot/with-file-cogs bot-name prefix cog-folders)))
