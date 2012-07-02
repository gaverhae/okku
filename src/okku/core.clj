(ns okku.core
  (import [akka.actor ActorRef ActorSystem Props UntypedActor
           UntypedActorFactory]
          [akka.routing RoundRobinRouter]
          [akka.util Duration]
          [java.util.concurrent TimeUnit])
  (require [clojure.walk :as w]))

(defmacro defactory [aname [self-name sender-name message & args] & body]
  (let [rec (first (filter #(= :dispatch-on (first %)) body))]
    (if-not rec (throw (RuntimeException. "defactory needs at least a dispatch-on clause")))
    (let [rec (->> rec
                (w/postwalk (fn [f] (cond
                                      (= f sender-name) `(.getSender ~'this)
                                      (= f self-name) `(.getSelf ~'this)
                                      (and (list? f)
                                           (= '! (first f))) (if (= (count f) 3)
                                                               `(.tell
                                                                  ~(nth f 1)
                                                                  ~(nth f 2)
                                                                  (.getSelf ~'this))
                                                               (throw (RuntimeException. "Send-message forms (using !) must only contain the sender and the message.")))
                                      :else f)))
                (apply hash-map))
          m `msg#]
      `(defn ~aname [~@args & {c# :context r# :router n# :name}]
         (let [p# (Props. (proxy [UntypedActorFactory] []
                           (~'create []
                             (proxy [UntypedActor] []
                               (~'onReceive [~(assoc message :as m)]
                                 ~(concat (cons 'cond (reduce (fn [acc [k v]] (concat acc [`(= ~(:dispatch-on rec) ~k)
                                                                                           v])) () (dissoc rec :dispatch-on)))
                                          `(:else (.unhandled ~'this ~m))))))))
               p# (if r# (.withRouter p# r#) p#)]
           (if n#
             (.actorOf c# p# n#)
             (.actorOf c# p#)))))))
