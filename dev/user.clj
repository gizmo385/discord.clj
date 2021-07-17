(ns user
  (:use
    [clojure.repl]
    [clojure.pprint])
  (:require
    [discord.bot :as bot]
    [integrant.core :as ig]
    [integrant.repl :refer [clear go halt prep init suspend resume reset reset-all]]
    [integrant.repl.state :as state]))

(let [config (ig/read-string (slurp "resources/config.edn"))]
  (integrant.repl/set-prep! (constantly config)))
