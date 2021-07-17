(ns discord.utils
  (:require
    [clojure.string :as s]
    [discord.types :refer [->snowflake]]))

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

(defn rgb->integer [r g b]
  (-> r
      (bit-shift-left 8)
      (+ g)
      (bit-shift-left 8)
      (+ b)))

(defn index-of
  "Calculates the index of a value v within a collection coll, using `.indexOf`."
  [coll v]
  (.indexOf coll v))
