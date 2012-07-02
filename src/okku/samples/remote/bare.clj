(ns okku.samples.remote.bare
  (import [akka.actor ActorRef Props UntypedActor UntypedActorFactory ActorSystem]
          [akka.kernel Bootable]
          [com.typesafe.config ConfigFactory]))

(defn msg-math-op [act op]
  {:type :math-op :actor act :op op})
(defn msg-add [n1 n2]
  {:type :math-op :subtype :add :1 n1 :2 n2})
(defn msg-add-res [n1 n2 res]
  {:type :math-result :subtype :add :1 n1 :2 n2 :result res})
(defn msg-sub [n1 n2]
  {:type :math-op :subtype :sub :1 n1 :2 n2})
(defn msg-sub-res [n1 n2 res]
  {:type :math-result :subtype :sub :1 n1 :2 n2 :result res})
(defn msg-mul [n1 n2]
  {:type :math-op :subtype :mul :1 n1 :2 n2})
(defn msg-mul-res [n1 n2 res]
  {:type :math-result :subtype :mul :1 n1 :2 n2 :result res})
(defn msg-div [n1 n2]
  {:type :math-op :subtype :div :1 n1 :2 n2})
(defn msg-div-res [n1 n2 res]
  {:type :math-result :subtype :div :1 n1 :2 n2 :result res})

(defn actor-advanced-calculator [context name]
  (.actorOf context
            (Props. (proxy [UntypedActorFactory] []
                      (create []
                        (proxy [UntypedActor] []
                          (onReceive [msg] (condp = (:subtype msg)
                                             :mul (do (println (format "Calculating %s * %s"
                                                                       (:1 msg) (:2 msg)))
                                                    (.tell (.getSender this)
                                                           (msg-mul-res
                                                             (:1 msg) (:2 msg) (* (:1 msg) (:2 msg)))))
                                             :div (do (println (format "Calculating %s / %s"
                                                                       (:1 msg) (:2 msg)))
                                                    (.tell (.getSender this)
                                                           (msg-div-res
                                                             (:1 msg) (:2 msg) (/ (:1 msg) (:2 msg)))))
                                             (.unhandled this msg)))))))
            name))

(defn actor-creation [context]
  (.actorOf context
            (Props. (proxy [UntypedActorFactory] []
                      (create []
                        (proxy [UntypedActor] []
                          (onReceive [msg] (condp = (:type msg)
                                             :math-op (.tell (:actor msg)
                                                             (:op msg)
                                                             (.getSelf this))
                                             :math-result (condp = (:subtype msg)
                                                            :mul (println (format
                                                                            "Mul result: %s * %s = %s"
                                                                            (:1 msg) (:2 msg) (:result msg)))
                                                            :div (println (format
                                                                            "Div result: %s / %s = %2.3f"
                                                                            (:1 msg) (:2 msg) (double (:result msg)))))
                                             (.unhandled this msg)))))))))

(defn actor-lookup [context]
  (.actorOf context
            (Props. (proxy [UntypedActorFactory] []
                      (create []
                        (proxy [UntypedActor] []
                          (onReceive [msg] (condp = (:type msg)
                                             :math-op (.tell (:actor msg)
                                                             (:op msg)
                                                             (.getSelf this))
                                             :math-result (condp = (:subtype msg)
                                                            :add (println (format
                                                                            "Add result: %s + %s = %s"
                                                                            (:1 msg) (:2 msg) (:result msg)))
                                                            :sub (println (format
                                                                            "Sub result: %s - %s = %s"
                                                                            (:1 msg) (:2 msg) (:result msg))))
                                             (.unhandled this msg)))))))))

(defn actor-simple-calculator [context name]
  (.actorOf context
            (Props. (proxy [UntypedActorFactory] []
                      (create []
                        (proxy [UntypedActor] []
                          (onReceive [msg] (condp = (:subtype msg)
                                             :add (do (println (format "Calculating %s + %s"
                                                                       (:1 msg) (:2 msg)))
                                                    (.tell (.getSender this)
                                                           (msg-add-res (:1 msg) (:2 msg) (+ (:1 msg) (:2 msg)))))
                                             :sub (do (println (format "Calculating %s - %s"
                                                                       (:1 msg) (:2 msg)))
                                                    (.tell (.getSender this)
                                                           (msg-sub-res (:1 msg) (:2 msg) (- (:1 msg) (:2 msg)))))
                                             (.unhandled this msg)))))))
            name))

(defn calculator-application []
  (let [system (ActorSystem/create "CalculatorApplication"
                                   (.getConfig (ConfigFactory/load)
                                               "calculator"))
        actor (actor-simple-calculator system "simpleCalculator")]
    (proxy [Bootable] []
      (startup [])
      (shutdown [] (.shutdown system)))))

(definterface IDoSomething
  (doSomething [x]))

(defn creation-application []
  (let [system (ActorSystem/create "CreationApplication"
                                   (.getConfig (ConfigFactory/load)
                                               "remotecreation"))
        actor (actor-creation system)
        remoteActor (actor-advanced-calculator system "advancedCalculator")]
    (proxy [Bootable IDoSomething] []
      (doSomething [op] (.tell actor (msg-math-op remoteActor op)))
      (startup [])
      (shutdown [] (.shutdown system)))))

(defn lookup-application []
  (let [system (ActorSystem/create "LookupApplication"
                                   (.getConfig (ConfigFactory/load)
                                               "remotelookup"))
        actor (actor-lookup system)
        remoteActor (.actorFor system
                               "akka://CalculatorApplication@127.0.0.1:2552/user/simpleCalculator")]
    (proxy [Bootable IDoSomething] []
      (doSomething [op] (.tell actor (msg-math-op remoteActor op)))
      (startup [])
      (shutdown [] (.shutdown system)))))

(defn calc-app [& args]
  (calculator-application)
  (println "Started Calculator Application - waiting for messages"))

(defn creation-app [& args]
  (let [app (creation-application)]
    (println "Started Creation Application")
    (while true
      (if (zero? (rem (rand-int 100) 2))
        (.doSomething app
                      (msg-mul (rand-int 100) (rand-int 100)))
        (.doSomething app
                      (msg-div (rand-int 10000) (inc (rand-int 99)))))
      (try (Thread/sleep 2000)
        (catch InterruptedException e)))))

(defn lookup-app [& args]
  (let [app (lookup-application)]
    (println "Started Lookup Application")
    (while true
      (if (zero? (rem (rand-int 100) 2))
        (.doSomething app
                      (msg-add (rand-int 100) (rand-int 100)))
        (.doSomething app
                      (msg-sub (rand-int 100) (rand-int 100))))
      (try (Thread/sleep 2000)
        (catch InterruptedException e)))))

(defn -main [& args]
  (map #(.start (Thread. %))
       [calc-app creation-app lookup-app]))
