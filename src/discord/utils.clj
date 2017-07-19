(ns discord.utils
  (:require [clojure.set :refer [map-invert]]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(defn get-id [object-or-id]
  (condp = (type object-or-id)
    java.lang.String  object-or-id
    java.lang.Integer object-or-id
    java.lang.Long    object-or-id
    (:id object-or-id)))

(defn bidirectional-map [m]
  (merge m (map-invert m)))

(defn words [s]
  (s/split s #"\s+"))

(defonce dict-replace-pattern
  #"\{(?<field>\w+)\}")

(defn dict-format [target replacements]
  (s/replace
    target
    dict-replace-pattern
    (fn [[_ field]]
      (->> field
           (keyword)
           (get replacements)
           (get-id)
           (str)))))
