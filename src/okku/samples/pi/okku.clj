(ns okku.samples.pi.okku
  (use [okku.core]))

(defn m-compute []
  {:type :compute})
(defn m-work [start n-elem]
  {:type :work :start start :n-elem n-elem})
(defn m-result [value]
  {:type :result :value value})
(defn m-approx [pi dur]
  {:type :approx :pi pi :dur dur})

(defn calculate-pi-for [^long st ^long n]
  (let [limit (* (inc st) n)]
    (loop [i (* st n) tot 0.0]
      (if (= i limit)
        tot
        (recur (unchecked-inc i) (+ tot
                                    (* 4.0 (/ (double (unchecked-add 1 (unchecked-negate (unchecked-multiply 2 (unchecked-remainder-int i 2)))))
                                              (double (unchecked-add 1 (unchecked-multiply 2 i)))))))))))

(defactor worker []
  (onReceive [{t :type s :start n :n-elem}]
    (dispatch-on t
      :work (! (m-result (calculate-pi-for s n))))))

(defactor master [nw nm ne l]
  (let [workerRouter (atom nil)
        res (atom {:pi 0 :nr 0})
        start (System/currentTimeMillis)]
    (preStart [] (reset! workerRouter (spawn worker [] :name "workerRouter"
                                             :router (round-robin-router nw))))
    (onReceive [{t :type v :value}]
      (dispatch-on t
        :compute (dotimes [n nm]
                   (! @workerRouter (m-work n ne)))
        :result (do (swap! res #(merge-with + % {:pi v :nr 1}))
                  (when (= (:nr @res) nm)
                    (! l (m-approx (:pi @res)
                                   (- (System/currentTimeMillis) start)))
                    (stop)))))))

(defactor listener []
  (onReceive [{t :type pi :pi dur :dur}]
    (dispatch-on t
      :approx (do (println (format "\n\tPi approximation: \t\t%1.8f\n\tCalculation time: \t%8d millis"
                                   pi dur))
                (shutdown)))))

(defn -main [& args]
  (let [nw (if args (Integer/parseInt (first args)) 4)
        ne 10000 nm 10000
        sys (actor-system "PiSystem")
        lis (spawn listener [] :in sys :name "listener")
        mas (spawn master [nw nm ne lis] :in sys :name "master")]
    (println "Number of workers: " nw)
    (.tell master (message-compute))
    (.awaitTermination sys)))
