(defproject pull-buoy "0.1.0-SNAPSHOT"
  :description "Migrate pull request data from one project to another"
  :url "http://github.com/cddr/pull-buoy"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [tentacles "0.2.5"]
                 [environ "1.0.0"]
                 [org.clojure/tools.trace "0.7.8"]]
  :plugins [[lein-environ "1.0.0"]])
  ;; :aot [pull-buoy.core]
  ;; :main pull-buoy.core)
