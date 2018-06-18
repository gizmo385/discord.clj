(ns discord.utils
  (:require [clojure.set :refer [map-invert]]
            [clojure.string :as s]
            [discord.types :refer [->snowflake]]))

(defn bidirectional-map [m]
  (merge m (map-invert m)))

(defn words [s]
  (s/split s #"\s+"))

(defonce map-replace-pattern
  #"\{(?<field>\w+)\}")

(defn map-format
  "Helper function to replace bracketed substrings in a string based on values in a map.
    Example:
      (map-format \"Hello {thing}\" {:thing \"World\"}) --> \"Hello World\"."
  [target replacements]
  (s/replace
    target
    map-replace-pattern
    (fn [[_ field]]
      (->> field
           keyword
           (get replacements)
           ->snowflake
           str))))
