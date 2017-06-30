(ns discord.utils
  (:require [clojure.set :refer [map-invert]]))

(defn get-id [object-or-id]
  (if (= java.lang.Long (type object-or-id))
    object-or-id
    (:id object-or-id)))

(defn bidirectional-map [m]
  (merge m (map-invert m)))
