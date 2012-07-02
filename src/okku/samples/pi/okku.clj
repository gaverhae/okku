(ns okku.samples.pi.okku
  (import [akka.actor ActorRef ActorSystem Props UntypedActor
           UntypedActorFactory]
          [akka.routing RoundRobinRouter])
  (use [okku.core :only [defactory]]))

(defn message-compute []
  {:type :compute})
(defn message-work [start nrElem]
  {:type :work, :start start, :nrOfElements nrElem})
(defn message-result [value]
  {:type :result :value value})
(defn message-pi-approx [pi dur]
  {:type :pi-approximation :pi pi :duration dur})

(defn calculate-pi-for [^long st ^long n]
  (let [limit (* (inc st) n)]
    (loop [i (* st n) tot 0.0]
      (if (= i limit)
        tot
        (recur (unchecked-inc i) (+ tot
                                    (* 4.0 (/ (double (unchecked-add 1 (unchecked-negate (unchecked-multiply 2 (unchecked-remainder-int i 2)))))
                                              (double (unchecked-add 1 (unchecked-multiply 2 i)))))))))))

(defactory actors-worker [self sender {t :type s :start n :nrOfElements}]
  [:dispatch-on t
   :work (! sender (message-result (calculate-pi-for s n)))])

(defn actor-master [context nw nm ne l name]
  (.actorOf context
            (Props. (proxy [UntypedActorFactory] []
                      (create []
                        (let [workerRouter (atom false)
                              res (atom {:pi 0 :nr 0})
                              start (System/currentTimeMillis)]
                          (proxy [UntypedActor] []
                            (onReceive [msg] (condp = (:type msg)
                                               :compute (dotimes [n nm]
                                                          (.tell (if-not @workerRouter
                                                                   (reset! workerRouter
                                                                           (actors-worker (.getContext this)
                                                                                          (RoundRobinRouter. nw)
                                                                                          "workerRouter"))
                                                                   @workerRouter)
                                                                 (message-work n ne)
                                                                 (.getSelf this)))
                                               :result (do (swap! res #(merge-with + % {:pi (:value msg)
                                                                                        :nr 1}))
                                                         (if (= (:nr @res) nm)
                                                           (do (.tell l (message-pi-approx (:pi @res)
                                                                                           (- (System/currentTimeMillis) start)))
                                                             (-> this .getContext (.stop (.getSelf this))))))
                                               (.unhandled this msg))))))))
            name))

(defn actor-listener [context name]
  (.actorOf context
            (Props. (proxy [UntypedActorFactory] []
                      (create []
                        (proxy [UntypedActor] []
                          (onReceive [msg] (condp = (:type msg)
                                             :pi-approximation (do
                                                                 (println (format "\n\tPi approximation: \t\t%1.8f\n\tCalculation time: \t%8d millis"
                                                                                  (:pi msg) (:duration msg)))
                                                                 (-> this .getContext .system .shutdown))
                                             (.unhandled this msg)))))))))

(defn -main [& args]
  (let [nw (if args (Integer/parseInt (first args)) 4)
        ne 10000 nm 10000
        sys (ActorSystem/create "PiSystem")
        listener (actor-listener sys "listener")
        master (actor-master sys nw nm ne listener "master")]
    (println "Number of workers: " nw)
    (.tell master (message-compute))
    (.awaitTermination sys)))
