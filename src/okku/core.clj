(ns okku.core
  (import [akka.actor ActorRef ActorSystem Props UntypedActor
           UntypedActorFactory Deploy Address AddressFromURIString]
          [akka.routing RoundRobinRouter]
          [akka.remote RemoteScope]
          [com.typesafe.config ConfigFactory])
  (require [clojure.walk :as w]))

(defn round-robin-router [n]
  "Creates a round-robin router with n replicas."
  (RoundRobinRouter. n))

(defn actor-system [name & {:keys [config file port local hostname]
                            :or {file "application"
                                 config false
                                 port 2552
                                 hostname "127.0.0.1"
                                 local false}}]
  "Creates a new actor system.
  config should be the name of the corresponding section in the application.conf file.
  file should be the name of the config file (.conf appended by the library).
  port should be the port number for this ActorSystem (lower priority than config file).
  local creates a local actor system (port option is then ignored, default to false)."
  (ActorSystem/create
    name
    (ConfigFactory/load
      (-> (ConfigFactory/parseResourcesAnySyntax file)
        (#(if config (.getConfig % config) %))
        (#(if-not local
            (.withFallback %
              (ConfigFactory/parseString
                (format "akka.remote.netty.port = %d
                        akka.remote.netty.hostname = \"%s\"
                        akka.actor.provider = akka.remote.RemoteActorRefProvider"
                        port hostname)))
            %))))))

(defmacro !
  "Sends the msg value as a message to target, or to current sender if target
  is not specified. Can only be used inside an actor."
  ([msg] `(.tell (.getSender ~'this) ~msg (.getSelf ~'this)))
  ([target msg] `(.tell ~target ~msg (.getSelf ~'this))))

(defmacro dispatch-on [dv & forms]
  `(cond ~@(mapcat (fn [[v f]] `[(= ~dv ~v) ~f]) (partition 2 forms))
         :else (.unhandled ~'this ~dv)))

(defmacro spawn [act & {c :in r :router n :name d :deploy-on}]
  (let [c (if c c '(.getContext this))
        p (-> act
            (#(if r `(.withRouter ~% ~r) %))
            (#(if d `(.withDeploy ~% (Deploy. (RemoteScope. (cond (instance? String ~d)
                                                                  (AddressFromURIString/parse ~d)
                                                                  (sequential? ~d)
                                                                  (condp = (count ~d)
                                                                    3 (Address. "akka"
                                                                                (nth ~d 0)
                                                                                (nth ~d 1)
                                                                                (nth ~d 2))
                                                                    4 (Address. (nth ~d 0)
                                                                                (nth ~d 1)
                                                                                (nth ~d 2)
                                                                                (nth ~d 3))
                                                                    (throw (IllegalArgumentException. "spawn:deploy-on should be either a String or a sequence of 3 or 4 elements")))
                                                                  :else (throw (IllegalArgumentException. "spawn:deploy-on should be either a String or a sequence of 3 or 4 elements")))))) %)))]
    (if n `(.actorOf ~c ~p ~n)
      `(.actorOf ~c ~p))))

(defn look-up [address & {s :in n :name}]
  (if-not s (throw (IllegalArgumentException. "okku.core/look-up needs an :in argument")))
  (let [[prot sys hn port & path] (clojure.string/split address #"://|@|:|/")
        {cprot "protocol" csys "actor-system" chn "hostname" cport "port"
         cpath "path"} (get-in (.root (.. s settings config)) ["okku" "lookup" (str "/" n)])
        [cprot csys chn cport cpath] (map #(if % (.unwrapped %)) [cprot csys chn cport cpath])
        path (clojure.string/join "/" path)
        cpath (if cpath (if (= (first path) \/) cpath (str "user/" cpath)))
        address (str (or cprot prot) "://"
                     (or csys sys) "@"
                     (or chn hn) ":"
                     (or cport port) "/"
                     (or cpath path))]
    (.actorFor s address)))

(defmacro stop []
  '(.stop (.getContext this) (.getSelf this)))

(defmacro shutdown []
  '(-> this .getContext .system .shutdown))

(defmacro actor [& forms]
  `(Props. (proxy [UntypedActorFactory] []
             (~'create []
               (proxy [UntypedActor] []
                 ~@forms)))))
