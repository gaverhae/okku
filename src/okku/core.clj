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

(defmacro !
  "Sends the msg value as a message to target, or to current sender if target
  is not specified. Can only be used inside an actor."
  ([msg] `(.tell (.getSender ~'this) ~msg (.getSelf ~'this)))
  ([target msg] `(.tell ~target ~msg (.getSelf ~'this))))

(defmacro dispatch-on [dv & forms]
  `(cond ~@(mapcat (fn [[v f]] `[(= ~dv ~v) ~f]) (partition 2 forms))
         :else (.unhandled ~'this ~dv)))

(defmacro spawn [name args & {c :in r :router n :name}]
  (let [c (if c c '(.getContext this))
        p (#(if r `(.withRouter ~% ~r) %) (cons name args))]
    (if n `(.actorOf ~c ~p ~n)
      `(.actorOf ~c ~p))))

(defmacro stop []
  '(.stop (.getContext this) (.getSelf this)))

(defmacro shutdown []
  '(-> this .getContext .system .shutdown))

(defn extract-let [forms]
  (if (and (= (count forms) 1)
           (= (first (first forms)) 'let))
    [(second (first forms)) (drop 2 (first forms))]
    [nil forms]))

(defmacro defactor [aname [& arglist] & forms]
  (let [[binds forms] (extract-let forms)]
    `(defn ~aname [~@arglist]
       (Props. (proxy [UntypedActorFactory] []
                 (~'create []
                   (let [~@binds]
                     (proxy [UntypedActor] []
                       ~@forms))))))))
