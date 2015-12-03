(ns okku.core
  "Library to facilitate the definition and creation of Akka actors from
  Clojure."
  (:import [akka.actor ActorRef ActorSystem Props UntypedActor
            Deploy Address AddressFromURIString]
           [akka.japi Creator]
           [akka.routing RoundRobinRouter]
           [akka.remote RemoteScope]
           [akka.pattern Patterns]
           [scala.concurrent Await]
           [scala.concurrent.duration Duration]
           [java.util.concurrent TimeUnit]
           [com.typesafe.config ConfigFactory])
  (:require clojure.string))

(defn round-robin-router
  "Creates a round-robin router with n replicas."
  [n] (RoundRobinRouter. n))

(defn- base-remote-config
  "Defines the minimal set of options required to use Akka in a distributed
  setting."
  [port hostname]
  (ConfigFactory/parseString
    (format "akka.remote.netty.tcp.port = %d
            akka.remote.netty.tcp.hostname = \"%s\"
            akka.actor.provider = akka.remote.RemoteActorRefProvider"
            port hostname)))

(defn- restrict-config
  "Restricts a ConfigObject to the given path; useful to separate the configuration
  file in multiple sections."
  [config-object path]
  (if path
    (.getConfig config-object path)
    config-object))

(defn- remote-config
  "Used to set the config and hostname parts of a config object."
  [config-object local? port hostname]
  (if-not local?
    (.withFallback config-object (base-remote-config port hostname))
    config-object))

(defn actor-system
  "Creates a new actor system.

  - `name` is used in the path to any actor in this system.
  - `:config` should be the name of the corresponding section in the config file.
  - `:file` should be the name of the config file (.conf appended by the library).
  - `:port` should be the port number for this ActorSystem (lower priority than config file).
  - `:hostname` should be the hostname for this ActorSystem (lower priority than config file).
  - `:local` creates a local actor system (port and hostname options are then ignored; defaults to false)."
  [name & {:keys [config file port local hostname]
           :or {file "application"
                config false
                port 2552
                hostname "127.0.0.1"
                local false}}]
  (ActorSystem/create
    name
    (ConfigFactory/load
      (-> (ConfigFactory/parseResourcesAnySyntax file)
        (restrict-config config)
        (remote-config local port hostname)))))

(defmacro !
  "Sends the msg value as a message to target, or to current sender if target
  is not specified. Can only be used inside an actor."
  ([msg] `(.tell (.getSender ~'this) ~msg (.getSelf ~'this)))
  ([target msg] `(.tell ~target ~msg (.getSelf ~'this))))


(defn ask
  "Use the Akka ask pattern. Returns a scala.concurrent.Future object which can
  be waited on by calling 'wait'"
  [^ActorRef actor timeout msg]
  (Patterns/ask actor msg timeout))

(def ? ask)

(defn wait
  "Wait on the specified scala.concurrent.Future to complete and return its
  result with an optional timeout duration."
  ([future]
     (Await/result future (Duration/Inf)))
  ([future duration]
     (Await/result future (Duration/create
			   (:value duration)
			   (:unit duration)))))

(defmacro dispatch-on
  "Bascially expands to a cond with an equality test on the dispatch value dv,
  then adds the final `:else` form to call the `.unhandled` method on self for
  compatibility with Akka expectations."
  [dv & forms]
  `(cond ~@(mapcat (fn [[v f]] `[(= ~dv ~v) ~f]) (partition 2 forms))
         :else (.unhandled ~'this ~dv)))

(defn with-router
  "Adds a router option to a Props object."
  [actor-spec r]
  (.withRouter actor-spec r))

(defn parse-address
  "Returns an akka.actor.Address from either a string representing the address
  or a four (or three) element sequence containing the four parts of an
  address: the protocol (defaults to \"akka\" if it's a 3 elements sequence),
  the ActorSystem's name, the hostname and the port."
  [a]
  (cond (instance? String a) (AddressFromURIString/parse a)
        (sequential? a) (condp = (count a)
                           3 (Address. "akka" (nth a 0) (nth a 1) (nth a 2))
                           4 (Address. (nth a 0) (nth a 1) (nth a 2) (nth a 3))
                           (throw (IllegalArgumentException. "spawn:deploy-on should be either a String or a sequence of 3 or 4 elements")))
        :else (throw (IllegalArgumentException. "spawn:deploy-on should be either a String or a sequence of 3 or 4 elements"))))

(defn with-deploy
  "Adds a deploy option to a Props object."
  [actor-spec address]
  (.withDeploy actor-spec (Deploy. (RemoteScope. (parse-address address)))))

(defmacro spawn
  "Spawns a new actor (side-effect) and returns an ActorRef to it. The first
  argument must be a Props object (such as created by the `actor` macro).

  Accepts the following options:

  - `:in` designates the ActorSystem in which to create the ActorRef. If no :in option is given, the new actor is created in the context of the current one.
  - `:router` specifies a Router object to serve as a router for the returned ActorRef (see Akka documentation).
  - `:name` is used for both the full (logical) path of the returned ActorRef and for looking-up the relevant configuration concerning the to-be-created Actor (generated if none given).
  - `:deploy-on` must be the address of a remote ActorSystem in one of the three forms accepted by parse-address; the actor is remotely spawned on the remote system (as a root actor)."
  [actor-spec & {c :in r :router n :name d :deploy-on
                 :or {c '(.getContext this)}}]
  (let [p (reduce (fn [acc [opt f]]
                    (if opt `(~f ~acc ~opt) acc))
                  actor-spec `([~r with-router]
                               [~d with-deploy]))]
    (if n `(.actorOf ~c ~p ~n)
      `(.actorOf ~c ~p))))

(defn- string-to-vec
  "Converts an address in string form to the corresponding vector form"
  [a] (let [v (clojure.string/split a #"://|@|:|/")]
        (conj (vec (take 4 v)) (drop 4 v))))

(defn- vec-to-string
  "Converts an address in vector form to its string representation"
  [a] (let [a (vec a)]
        (format "%s://%s@%s:%s/%s" (a 0) (a 1) (a 2) (a 3)
                (clojure.string/join "/" (a 4)))))

(defn- get-config-lookup
  "Extracts the configuration for an actor lookup from the configuration file.
  Returns it as a vector in the format expected by merge-addresses and vec-to-string."
  [config name]
  (let [c (get-in config ["okku" "lookup" (str "/" name)])
        extract (fn [k] (if-let [v (get c k)] (.unwrapped v)))]
    [(extract "protocol") (extract "actor-system")
     (extract "hostname") (extract "port")
     (if-let [ps (extract "path")]
       (let [p (clojure.string/split ps #"/")]
         (if (= (first p) "")
           (rest p)
           (cons "user" p))))]))

(defn- merge-addresses
  "Merges two addresses given in vector form `[prot sys hn port & path]`."
  [a1 a2] (map #(if % % %2) a1 a2))

(defn look-up
  "Returns an ActorRef for the specified actor path.
  :in specifies the ActorSystem in which to create the ActorRef
  :name is a local name for the actor, used to allow for file-based
  configuration of the look-up path."
  [address & {s :in n :name}]
  (if-not s (throw (IllegalArgumentException. "okku.core/look-up needs an :in argument")))
  (let [code-address-v (string-to-vec address)
        config-address-v (get-config-lookup (.. s settings config root) n)
        address-v (merge-addresses config-address-v code-address-v)
        address (vec-to-string address-v)]
    (.actorFor s address)))

(defmacro stop
  "Simple helper macro to access the stop method of the current actor."
  [] '(.stop (.getContext this) (.getSelf this)))

(defmacro shutdown
  "Simple helper macro to send the shutdown signal to the enclosing ActorSystem."
  [] '(-> this .getContext .system .shutdown))

(defmacro actor
  "Macro used to define an actor. Actually returns a Props object that can be
  passed to the .actorOf method of an ActorSystem, or similarly that can be used
  as the first argument to spawn."
  [& forms]
  `(Props/create ~UntypedActor (proxy [Creator] []
             (~'create []
               (proxy [UntypedActor] []
                 ~@forms)))))
