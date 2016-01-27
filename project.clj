(defproject org.clojure.gaverhae/okku "0.2.0"
  :description "Clojure wrapper around the Akka library with an extensible function/actor call library."
  :url "https://github.com/gaverhae/okku"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.typesafe.akka/akka-actor_2.11 "2.3.14"]
                 [com.typesafe.akka/akka-remote_2.11 "2.3.14"]
                 [com.typesafe.akka/akka-kernel_2.11 "2.3.14"]]
  :plugins [[michaelblume/lein-marginalia "0.9.0"]])
