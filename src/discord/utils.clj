(ns discord.utils)

(defn get-id [object-or-id]
  (if (= java.lang.Long (type object-or-id))
    object-or-id
    (:id object-or-id)))
