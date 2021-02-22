(ns discord.bot-test
  (:require
    [discord.bot :as bot]
    [clojure.test :refer [deftest is are testing]]))

(deftest command->invocation-test
  (testing "Correctly traverses module map"
    (let [move-fn (fn [])
          ban-fn (fn [])
          unban-fn (fn [])
          say-fn (fn [])
          module-map {:admin {:region {:move move-fn}
                              :player {:ban ban-fn
                                       :unban unban-fn}}
                      :say say-fn}

          move-command "admin region move us-west"
          ban-command "admin player ban @testing"
          say-command "say testing"
          invalid-command "this command does not exist"
          invalid-admin-command "admin region delete this-region"

          move-region-invocation (bot/command->invocation move-command module-map)
          ban-invocation (bot/command->invocation ban-command module-map)
          say-invocation (bot/command->invocation say-command module-map)]
      (is (= (:handler move-region-invocation) move-fn))
      (is (= (:handler ban-invocation) ban-fn))
      (is (= (:handler say-invocation) say-fn))

      (is (= (:args move-region-invocation) ["us-west"]))
      (is (= (:args say-invocation) ["testing"]))
      (is (= (:args ban-invocation) ["@testing"]))

      (are [i] (thrown? clojure.lang.ExceptionInfo (bot/command->invocation i module-map))
           invalid-command
           invalid-admin-command))))
