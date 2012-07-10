(ns okku.samples.remote.okku
  (import [akka.kernel Bootable])
  (use [okku.core]))

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

(defactor advanced-calculator []
  (onReceive [{t :subtype a :1 b :2}]
    (dispatch-on t
      :mul (do (println (format "Calculating %s * %s" a b))
             (! (msg-mul-res a b (* a b))))
      :div (do (println (format "Calculating %s / %s" a b))
             (! (msg-div-res a b (/ a b)))))))

(defactor created []
  (onReceive [{t :type s :subtype a :1 b :2 r :result act :actor o :op}]
    (dispatch-on [t s]
      [:math-op nil] (! act o)
      [:math-result :mul] (println (format "Mul result: %s * %s = %s" a b r))
      [:math-result :div] (println (format "Div result: %s / %s = %2.3f" a b (double r))))))

(defactor looked-up []
  (onReceive [{t :type s :subtype a :1 b :2 r :result act :actor o :op}]
    (dispatch-on [t s]
      [:math-op nil] (! act o)
      [:math-result :add] (println (format "Add result: %s + %s = %s" a b r))
      [:math-result :sub] (println (format "Sub result: %s - %s = %s" a b r)))))

(defactor simple-calculator []
  (onReceive [{t :subtype a :1 b :2}]
    (dispatch-on t
      :add (do (println (format "Calculating %s + %s" a b))
             (! (msg-add-res a b (+ a b))))
      :sub (do (println (format "Calculating %s - %s" a b))
             (! (msg-sub-res a b (- a b)))))))
      
(defn calculator-application []
  (let [system (actor-system "CalculatorApplication"
                             :config "calculator")
        actor (spawn simple-calculator []
                     :in system :name "simpleCalculator")]
    (proxy [Bootable] []
      (startup [])
      (shutdown [] (.shutdown system)))))

(definterface IDoSomething
  (doSomething [x]))

(defn creation-application []
  (let [system (actor-system "CreationApplication"
                             :config "remotecreation")
        actor (spawn created [] :in system)
        remoteActor (spawn advanced-calculator []
                           :in system
                           :name "advancedCalculator")]
    (proxy [Bootable IDoSomething] []
      (doSomething [op] (.tell actor (msg-math-op remoteActor op)))
      (startup [])
      (shutdown [] (.shutdown system)))))

(defn lookup-application []
  (let [system (actor-system "LookupApplication"
                             :config "remotelookup")
        actor (spawn looked-up [] :in system)
        remoteActor (look-up "akka://CalculatorApplication@127.0.0.1:2552/user/simpleCalculator"
                             :in system)]
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
