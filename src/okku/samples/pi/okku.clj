(ns okku.samples.pi.okku
  (use [okku.core]))

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
   :work (! (message-result (calculate-pi-for s n)))])

(defactory actor-master [self sender {t :type v :value} nw nm ne l]
  [:local-state
   workerRouter (atom false)
   res (atom {:pi 0 :nr 0})
   start (System/currentTimeMillis)]
  [:pre-start
   (reset! workerRouter (actors-worker :context (.getContext this)
                                       :router (round-robin-router nw)
                                       :name "workerRouter"))]
  [:dispatch-on t
   :compute (dotimes [n nm]
              (tell @workerRouter (message-work n ne)))
   :result (do (swap! res #(merge-with + % {:pi v :nr 1}))
             (when (= (:nr @res) nm)
               (tell l (message-pi-approx (:pi @res)
                                          (- (System/currentTimeMillis) start)))
               (-> this .getContext (.stop self))))])

(defactory actor-listener [self sender {t :type pi :pi dur :duration}]
  [:dispatch-on t
   :pi-approximation (do
                       (println (format "\n\tPi approximation: \t\t%1.8f\n\tCalculation time: \t%8d millis"
                                        pi dur))
                       (-> this .getContext .system .shutdown))])

(defn -main [& args]
  (let [nw (if args (Integer/parseInt (first args)) 4)
        ne 10000 nm 10000
        sys (actor-system "PiSystem")
        listener (actor-listener :context sys :name "listener")
        master (actor-master nw nm ne listener :context sys :name "master")]
    (println "Number of workers: " nw)
    (.tell master (message-compute))
    (.awaitTermination sys)))
