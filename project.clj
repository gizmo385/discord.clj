(defproject discord.clj/discord.clj "3.0.0-SNAPSHOT"
  :description "A library for creating Discord bots in Clojure."
  :url "https://github.com/gizmo385/discord.clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.cli "1.0.206"]
                 [clj-http/clj-http "3.6.1"]
                 [clj-time/clj-time "0.14.4"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.clojure/data.json "0.2.6"]
                 [overtone/at-at "1.2.0"]
                 [stylefruits/gniazdo "1.0.0"]
                 [integrant/integrant "0.8.0"]
                 [integrant/repl "0.3.2"]
                 [nrepl/nrepl "0.8.3"]]
  :main ^:skip-aot discord.core
  :target-path "target/%s"
  :source-paths ["src" "dev"]
  :repl-options {:init-ns user}
  :profiles {:uberjar {:aot :all}
             :examples {:source-paths ["examples"]}})
