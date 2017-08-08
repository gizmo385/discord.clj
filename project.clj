(defproject discord.clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "3.6.1"]
                 [stylefruits/gniazdo "1.0.0"]]
  :main ^:skip-aot discord.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
