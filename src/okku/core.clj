(ns okku.core
  (import [akka.actor ActorRef ActorSystem Props UntypedActor
           UntypedActorFactory]
          [akka.routing RoundRobinRouter]
          [com.typesafe.config ConfigFactory])
  (require [clojure.walk :as w]))

(defn round-robin-router [n]
  "Creates a round-robin router with n replicas."
  (RoundRobinRouter. n))

(defn actor-system [name & {:keys [config]}]
  "Creates a new actor system. config should be the name of the corresponding
  section in the application.conf file."
  (if config
    (ActorSystem/create name (.getConfig (ConfigFactory/load) config))
    (ActorSystem/create name)))

(defn- extract [sym f]
  (first (filter #(= sym (first %)) f)))

(defmacro !
  "Sends the msg value as a message to target, or to current sender if target
  is not specified. Can only be used inside an actor."
  ([msg] `(.tell (.getSender ~'this) ~msg (.getSelf ~'this)))
  ([target msg] `(.tell ~target ~msg (.getSelf ~'this))))

(defmacro defactory [aname [self-name sender-name message & args] & body]
  (let [rec (extract :dispatch-on body)
        state (rest (extract :local-state body))
        pre-start (rest (extract :pre-start body))]
    (if-not rec (throw (RuntimeException. "defactory needs at least a dispatch-on clause")))
    (let [rec (->> rec
                (w/postwalk (fn [f] (cond
                                      (= f sender-name) `(.getSender ~'this)
                                      (= f self-name) `(.getSelf ~'this)
                                      :else f)))
                (apply hash-map))
          m `msg#]
      `(defn ~aname [~@args & {c# :context r# :router n# :name}]
         (let [p# (Props. (proxy [UntypedActorFactory] []
                           (~'create []
                             (let [~@state]
                               (proxy [UntypedActor] []
                                 (~'onReceive [~(assoc message :as m)]
                                   ~(concat (cons 'cond (reduce (fn [acc [k v]] (concat acc [`(= ~(:dispatch-on rec) ~k)
                                                                                             v])) () (dissoc rec :dispatch-on)))
                                            `(:else (.unhandled ~'this ~m))))
                                 ~@(if (seq pre-start)
                                     `((~'preStart [] ~@pre-start))))))))
               p# (if r# (.withRouter p# r#) p#)]
           (if n#
             (.actorOf c# p# n#)
             (.actorOf c# p#)))))))
