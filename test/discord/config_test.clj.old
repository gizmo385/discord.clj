(ns discord.config-test
  (:require [discord.config :as config]
            [clojure.data.json :as json]
            [clojure.test :refer [deftest is are testing]])
  (:import [java.io IOException]))

(deftest test-load-file
  (let [example-data {:test "hello-world"}
        test-json (json/write-str example-data)]
    (testing "Loading a file works"
      (with-redefs [slurp (fn [& _] test-json)]
        (is (= (config/file-io "some-file" :load) example-data))))

    (testing "Loading a file that doesn't exist without a default yields []."
      (with-redefs [slurp (fn [& _] (throw (new IOException)))]
        (is (= (config/file-io "some-file" :load) []))))

    (testing "Loading a file that doesn't exist with a default yields that default value."
      (with-redefs [slurp (fn [& _] (throw (new IOException)))]
        (is (= (config/file-io "some-file" :load :default {}) {}))))))

(deftest test-save-file
  (let [example-data {:test "hello-world"}
        test-json (json/write-str example-data)]
    (testing "Saving a file works"
      (with-redefs [spit (fn [_ content & _] content)]
        (is (= (config/file-io "some-file" :save :data example-data) test-json))))))

(deftest test-check-file
  (testing "Check a non-existent file"
    (let [success-string "HEY LOOK IT WORKED"]
      (with-redefs [config/create-file (fn [_] success-string)
                    config/file-exists? (fn [f] false)]
        (is (= success-string (config/file-io "test" :check))))))

  (testing "Check a file that exists"
    (with-redefs [config/create-file (fn [_] (is false "Should not attempt to create a file"))
                  config/file-exists? (fn [f] true)]
      (is (nil? (config/file-io "test" :check))))))
