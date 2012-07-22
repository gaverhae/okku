(ns okku.test.core
  (:use [okku.core])
  (:use [clojure.test]))

(deftest test-!
  (are [x y] (= (macroexpand-1 (quote x)) y)
       (okku.core/! msg) '(.tell (.getSender this) msg (.getSelf this))
       (okku.core/! target msg) '(.tell target msg (.getSelf this))))

(deftest test-spawn
  (are [x y] (= (macroexpand-1 (quote x)) y)
       (okku.core/spawn act) '(.actorOf (.getContext this) act)
       (okku.core/spawn act :in asys :router router :name name)
       '(.actorOf asys (.withRouter act router) name)
       (okku.core/spawn act :deploy-on addr)
       '(.actorOf
          (.getContext this)
          (.withDeploy act
                       (akka.actor.Deploy.
                         (akka.remote.RemoteScope.
                           (clojure.core/cond
                             (clojure.core/instance? java.lang.String addr) (akka.actor.AddressFromURIString/parse
                                                                              addr)
                             (clojure.core/sequential? addr) (clojure.core/condp
                                                               clojure.core/=
                                                               (clojure.core/count addr)
                                                               3
                                                               (akka.actor.Address.
                                                                 "akka"
                                                                 (clojure.core/nth addr 0)
                                                                 (clojure.core/nth addr 1)
                                                                 (clojure.core/nth
                                                                   addr
                                                                   2))
                                                               4
                                                               (akka.actor.Address.
                                                                 (clojure.core/nth addr 0)
                                                                 (clojure.core/nth addr 1)
                                                                 (clojure.core/nth addr 2)
                                                                 (clojure.core/nth
                                                                   addr
                                                                   3))
                                                               (throw
                                                                 (java.lang.IllegalArgumentException.
                                                                   "spawn:deploy-on should be either a String or a sequence of 3 or 4 elements")))
                             :else (throw
                                     (java.lang.IllegalArgumentException.
                                       "spawn:deploy-on should be either a String or a sequence of 3 or 4 elements")))))))
       ))

(deftest test-dispatch-on
  (are [x y] (= (macroexpand-1 x) y)
       '(okku.core/dispatch-on t
                               :dv1 (answer1)
                               :dv2 (answer2))
       '(clojure.core/cond (clojure.core/= t :dv1) (answer1)
              (clojure.core/= t :dv2) (answer2)
              :else (.unhandled this t))))

(deftest test-string-to-vec
  (are [x y] (= x (@#'okku.core/string-to-vec y))
       ["akka" "sys" "hostname" "port" ["path1" "path2"]]
       "akka://sys@hostname:port/path1/path2"
       ["akka" "CalculatorApplication" "127.0.0.1" "2552" ["user" "simpleCalculator"]]
       "akka://CalculatorApplication@127.0.0.1:2552/user/simpleCalculator"))

(deftest test-vec-to-string
  (are [x y] (= y (@#'okku.core/vec-to-string x))
       ["akka" "sys" "hostname" "port" ["path1" "path2"]]
       "akka://sys@hostname:port/path1/path2"
       ["akka" "CalculatorApplication" "127.0.0.1" "2552" ["user" "simpleCalculator"]]
       "akka://CalculatorApplication@127.0.0.1:2552/user/simpleCalculator"))

(deftest test-get-config-lookup
  (are [x y] (= x (@#'okku.core/get-config-lookup
                       (..
                         (com.typesafe.config.ConfigFactory/parseString y)
                         root)
                       "name"))
       ["akka" "sys" "hostname" "port" ["path1" "path2"]]
       "okku.lookup./name {
       protocol = akka
       actor-system = sys
       hostname = hostname
       port = port
       path = /path1/path2
       }
       "
       ["akka" nil nil "port" ["user" "path1" "path2"]]
       "okku.lookup./name {
       protocol = akka
       port = port
       path = path1/path2
       }
       "
       [nil nil nil nil nil] ""))

(deftest test-merge-addresses
  (are [x y z] (= x (@#'okku.core/merge-addresses y z))
       ["akka" "sys" "hn" "port" ["path1" "path2"]]
       ["akka" nil nil "port" nil]
       ["other" "sys" "hn" "other" ["path1" "path2"]]))
