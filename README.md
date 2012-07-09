# okku

Okku is a Clojure wrapper for the Akka library. Akka is an erlang-inspired Scala library implementing the Actor model for concurrency and distribution.

The Akka library has a Java interface, but using it directly from Clojure is rather unwieldy as it is mainly based on subclassing, and requires some juggling with factories (see "bare" examples in src/okka/samples/\*/).

For explanations on the Actor model itself and how and when to use it, see the documentation of either Akka or Erlang.

## Usage

From Clojure, Akka is mostly useful for the relatively transparent distribution it allows; concurrency is already well supported in Clojure.

Again, for a full description of the Akka actor model and how to use it, see the [Akka documentation](http://akka.io/docs/). This file will only describe how to translate from the Java Akka API to the Okku equivalent form, so that Okku users can easily refer to the Akka documentation. From now on, references to Akka will implicitly reference the Java API (Akka is primarily written in Scala).

### Why a wrapper?

Clojure boasts symbiotic integration with the JVM and direct access to Java, so one might wonder why Akka needs a Clojure wrapper to be usable. The problems come from the style of the Java API for Akka.

First, actor definition in Akka is made by subclassing the akka.actor.UntypedActor class. Subclassing classes in Clojure can be done either through ``gen-class`` or ``proxy``, neither of which is particularly elegant.

Second, actor instanciation in Akka is never done manually. The way things are done is you have to ask an actor (or an actor system, see brief explanation below or complete explanations in the Akka manual) to create a child for itself, and you do that by passing it the class of the new actor you want to create. The call looks something like this (see [Akka First Steps](http://doc.akka.io/docs/akka/2.0.2/intro/getting-started-first-java.html) for full context) :

```java
workerRouter = this.getContext().actorOf(new Props(Worker.class).withRouter(new RoundRobinRouter(nrOfWorkers)),
               "workerRouter");
```

This approach would rule out proxy, which leaves only gen-class, which is a bit unwieldy. It is worth noting that manually instanciating UntypedActor or any subclass thereof yields an error, be it through ``new`` in Java or through ``proxy`` in Clojure.

Fortunately, there is a relatively undocumented (or at least little advertised) way around the problem. Instead of passing a class to the Props object, one can actually pass an instanciated subclass of akka.actor.UntypedActorFactory; this is sometimes demonstrated in the Akka documentation with an anonymous class, such as in:

```java
ActorRef master = system.actorOf(new Props(new UntypedActorFactory() {
    public UntypedActor create() {
        return new Master(nrOfWorkers, nrOfMessages, nrOfElements, listener);
    }
}), "master");
```

Which means we can actually call ``new`` on an UntypedActor, as long as it is only done inside an UntypedActorFactory.

This finally leads to the following, not very elegant way of defining an actor from Clojure:

```clojure
(defn create-actor [context router name]
  (.actorOf context
            (-> (Props. (proxy [UntypedActorFactory] []
                          (create []
                            (proxy [UntypedActor] []
                              (onReceive [msg] (do ;receive and process the message
                                                 ))))))
              (.withRouter router))
            name))
```

The main difference with the equivalent Java code is a change of perspective: instead of creating a class that defines an actor type A, and later asking an actor system to spawn an actor of type A, we create a function that we can call, passing it an actor system, and that will return an actor.

This is basically the same shift from object-centric thinking to function-centric thinking that pervades the transition from Java to Clojure.

### Actor systems and hierarchies

To enable the construction of self-healing systems, the Akka actor systems enforces a hierarchical structure. This means that every actor is the child of another actor, and every actor know its own children. This in turn allows an actor to monitor the health of its children, and to react to their failure. Again, refer to the Akka documentation for more details than provided here.

There are a number of strategies that can be chosen from for the behaviour of a parent relative to the failure of one of its children. This is not yet covered by Okku, so it will not be covered further in this readme. Know that Okku yields raw Akka objects, so while it is not yet implemented as a wrapper, all of that functionality is still available.

Of course, every actor needing a parent means we have a chicken-and-egg problem, which Akka solves by creating special actors for you, which do not have (user-accessible) parents, and which are called Actor Systems. Basically, an Akka application begins by creating an Actor System, and then tells this actor system to spawn the required actors for the rest of the computation. Actors from this first generation of manually-created actors are typically thought of as the roots of the actor hierarchy within an application.

Practically, spawning a new root actor is done through the [``actorOf`` method](http://doc.akka.io/api/akka/2.0/akka/actor/ActorSystem.html).

To create a non-root actor, the programmer must tell an actor to create a child for itself. This is done by first accessing the so-called `context` of an actor, and then asking that context to create a new actor in exactly the same way as for an actor system. (See [documentation](http://doc.akka.io/api/akka/2.0/akka/actor/UntypedActorContext.html).)

### Creating an actor with Okku

As explained in an earlier section, from Clojure, we want to first define a function that will later yield a specific actor. The Okku API is far from fixed (that is why the major release version is still 0); here is how it works right now.

The main point of entry of the library is the ``defactory`` macro, which stands for "define actor factory". As the name suggests, it is used to more easily define a new function that can yield an actor. Here is an example usage:

```clojure
(defactory actors-worker [self sender {t :type s :start n :nrOfElements}]
  [:dispatch-on t
   :work (! (message-result (calculate-pi-for s n)))])
```

which expands roughly to:
```clojure
(defn actors-worker [& {c_auto :context,
                        n_auto :name,
                        r_auto :router}]
  (let [p_auto (akka.actor.Props. (proxy [akka.actor.UntypedActorFactory] []
                                    (create []
                                      (let []
                                        (proxy [akka.actor.UntypedActor] []
                                          (onReceive [{:as msg_auto,
                                                       t :type,
                                                       s :start,
                                                       n :nrOfElements}]
                                            (cond (= t :work)
                                                  (.tell (.getSender this)
                                                         (message-result
                                                           (calculate-pi-for s n))
                                                         (.getSelf this))
                                                  :else (.unhandled
                                                          this
                                                          msg_auto))))))))
        p_auto (if r_auto
                 (.withRouter p_auto r_auto)
                 p_auto)]
    (if n_auto
      (.actorOf c__auto p_auto n_auto)
      (.actorOf c__auto p_auto))))
```
where by roughly I mean that I have removed the qualified names for the clojure.core namespace and shortened the auto-generated variable names for readability.

The defined function, ``actors-worker``, can then be called with a required context and optional name and router keyword arguments to create a new actor in the given context.

Let's explain this a bit more. Th first argument to the ``defactory`` macro is obviously going ot be the function name. The second argument is a vector of three names: the name of the current actor (which will be expanded to ``(.getSelf this)``), the name of the sender (expanded to ``(.getSender this)``) and the name of the message, which may be a destructuring form, as is the case here (and as I'd expect to be the case in the vast majority of cases). Following these are vectors beginning with a specific keyword. The only mandatory one is the ``:dispatch-on`` form, which is present here.

The dispatch-on clause must be of the form:

```clojure
[:dispatch-on dispatch-expr
 val1 action1
 val2 action2
 ...]
```

and will be expanded to a ``cond`` form that tests for equality with the ``dispatch-expr``:

```clojure
(onReceive [msg-expr]
  (cond
    (= dispatch-expr val1) action1
    (= dispatch-expr val2) action2
    ...
    :else (.unhandled this msg)))
```

Other clauses are supported (and more will be added).

An actor is basically defined by its ``onReceive`` method, which means from a Clojure point of view it is essentially a function. To mimick local state, we can turn this function into a closure by wrapping the call to ``(proxy [UntypedActor]`` inside a ``let`` form in the ``create`` method of the enclosing ``UntypedActorFactory``. This is supported by the ``defactory`` macro through a ``local-state`` form:

```clojure
[:local-state
 name1 val1
 name2 val2
 ...]
```

which would simply expand to

```clojure
... (proxy [UntypedActorFactory] []
      (create []
        (let [name1 val1
              name2 val2
              ...]
          (proxy [UntypedActor] []
...
```

Finally, the ``UntypedActor`` class supports redefining a number of other methods than ``onReceive``, which will eventually be supported in the same way as ``preStart`` already is:

```clojure
[:pre-start (println "hello")]
```

would add to the ``(proxy [UntypedActor] [])`` a clause for the ``preStart`` method :

```clojure
(proxy [UntypedActor] []
  (onReceive [msg] ...)
  (preStart [] (println "hello")))
```


## License

Copyright (C) 2012 Gary Verhaegen.

Distributed under the Eclipse Public License, the same as Clojure.
