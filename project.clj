(defproject rivulet "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [cheshire "5.2.0" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [org.immutant/immutant "1.1.0-SNAPSHOT"]
                 [io.vertx/clojure-api "0.2.0-SNAPSHOT"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.4"]
                 [enfocus "2.0.0-beta1"]]
  :repositories [["JBoss Polyglot"
                  "http://downloads.immutant.org/upstream/"]]
  :plugins [[lein-cljsbuild "0.3.2"]]
  :immutant {:init rivulet.core/start}
  :cljsbuild {:builds [{:source-paths ["src-cljs"]
                        :compiler {:output-to "resources/public/client.js"
                                   :extern ["http://cdn.sockjs.org/sockjs-0.3.4.min.js"]
                                   :foreign-libs [{:file "resources/vertxbus.js"
                                                   :provides ["vertx.eventbus"]}]}}]})
