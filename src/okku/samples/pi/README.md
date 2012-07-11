# Getting Started Tutorial (Okku): First Chapter

## Introduction

Welcome to the first tutorial on how to get started with Okku and Clojure. This
tutorial is heavily inspired from the [Akka from Java tutorial](http://doc.akka.io/docs/akka/2.0.2/intro/getting-started-first-java.html). Just as they assume the reader knows Java and the general ideas behind,
this tutorial assumes that the reader knows Clojure and the general ideas behind
Okku (which are nearly exactly the same as the ones behind Akka).

In this tutorial, we will first set up a new project with
[Leiningen](http://leiningen.org/), and then see how to use Okku actors to
compute Pi using the following algorithm:
![pi](http://doc.akka.io/docs/akka/2.0.2/_images/pi-formula.png)

## Tutorial source code

You can see the full source code in the ``okku.clj`` file next to this one.

The sample project containing only this tutorial is also available on github at
the following address: ``git://github.com/gaverhae/okku-pi.git``

## Prerequisites

This tutorial assumes you have Java 1.6 or later and access to the Leiningen
script.

## Downloading and installing Okku

If you have cloned the git repository for the tutorial, you can simply run
```
lein run
```
to download all the dependencies, compile all the code and run the program.

If, however, you prefer to create your own version of the program step by step,
you can create a new empty project by issuing the
```
lein new okku-pi
```
command. Once the project is initialized, you should simply add the line
```clojure
[org.clojure.gaverhae/okku "0.1.0"]
```
to the ``:dependencies`` vector of your ``project.clj``.

## Start writing the code

All of the code will be written in the single file ``src/okku_pi/core.clj``.

In order to make a runnable program, edit the ``project.clj`` file to add the
following key to your ``defproject`` map:
```clojure
:main okku-pi.core
```
You can now run your program with
```
lein run
```
though it won't do much right now.

The last step before we start writing the actual code is to add a dependency on
Okku to the ``okku-pi.core`` namespace. Your namespace declaration should be
modified to look like:
```clojure
(ns okku-pi.core
  (use okku.core))
```

## Creating the messages

> The design we are aiming for is to have one Master actor initiating the
> computation, creating a set of Worker actors. Then it splits up the work into
> discrete chunks, and sends these chunks to the different workers in a
> round-robin fashion. The master waits until all the workers have completed
> their work and sent back results for aggregation. When computation is completed
> the master sends the result to the Listener, which prints out the result.

With this in mind, we need four types of messages:
- ``compute``: sent from the main function to the Master to start the computation
- ``work``: sent from the Master to a Worker, contains an assignment
- ``result``: sent from a Worker to the Master with a result
- ``approx``: sent from the Master to the Listener with the final result (and the computation time)

Since we're working in Clojure, an easy way to represent these messages is to
use maps, and as we'll have to create them multiple times, let's define some
functions to do that:
```clojure
(defn m-compute []
  {:type :compute})
(defn m-work [start n-elem]
  {:type :work :start start :n-elem n-elem})
(defn m-result [value]
  {:type :result :value value})
(defn m-approx [pi dur]
  {:type :approx :pi pi :dur dur})
```

## Defining the worker

We can now define one of our workers. First of all, our worker will be asked to
compute parts of the sum; specifically, they will receive a starting point and
a number of elements to compute, and have to compute the result. To help them
do that, let's define a function ``calculate-pi-for``:
```clojure
(defn calculate-pi-for [^long st ^long n]
  (let [limit (* (inc st) n)]
    (loop [i (* st n) tot 0.0]
      (if (= i limit)
        tot
        (recur (unchecked-inc i) (+ tot
                                    (* 4.0 (/ (double (unchecked-add 1 (unchecked-negate (unchecked-multiply 2 (unchecked-remainder-int i 2)))))
                                              (double (unchecked-add 1 (unchecked-multiply 2 i)))))))))))
```
> As an aside, this function is rather ugly. Do not hesitate to contact me if
> you know how to make it more beautiful while retaining its performances.

We can now define our worker actor. To do that, Okku defines a helper macro
called ``defactor``, which, at its most basic level, is a wrapper around a call
to ``proxy`` to define a subclass of ``akka.actor.UntypedActor``. ``UntypedActor``
has many overridable methods, which you can read about in the
[Akka documentation](http://doc.akka.io/docs/akka/2.0.2/), but the one method
you should always redefine is ``onReceive``, which receives a message as an
argument and reacts to it.

Since most of the time, when you receive a message, you have to react in a
different way based on what message it is, Okku provides a second helper macro
to handle dispatching on parts of the message. The worker actor can thus be
defined by:
```clojure
(defactor worker []
  (onReceive [{t :type s :start n :n-elem}]
    (dispatch-on t
      :work (! (m-result (calculate-pi-for s n))))))
```
where the ``!`` macro means "answer to the sender" when it has only one argument
(it can be used with two arguments, ``(! target msg)``, to send a message to
an arbitrary actor).

## Creating the master

The master actor is a bit more complex than the worker, as it can receive
multiple message types and has to somehow keep track of some internal state.

The ``defactor`` macro allows for internal state through the use of a simple
``let`` form in the actor definition. 

The first thing that the Master actor must do when initialized is to create the
workers. You might have wondered what the ``[]`` was doing in the worker definition
form; it is used to templatize the actor definition by defining "parameters"
that can be set at actor creation time. For the Worker actor, there were no
such parameters, but for the Master actor we will need four of them: the number
of worker actors to create, the number of ``work`` messages we want to send
(which will be equal to the number of "chuncks" to compute), the number of
elements in each chunck, and a reference to the listener to which it must send
the final result.

Finally, upon initialization, the master actor has to create the workers, and
that is done through the ``spawn`` macro. This macro takes as first argument
an actor definition previously defined with ``defactor``, the corresponding
arguments in a vector, and any three of the following keyword arguments: ``in``,
``router`` and ``name``.

The last useful macro we're going to need for the master actor is ``stop``,
which simply stops the current actor (along with all its children, in this case
the workers).

We can now write the master actor in a pretty straightforward way:
```clojure
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
```

## Creating the result listener

The listener needs only one capability that we have not discussed yet: it must
shut down the entire system when it receives the final message. This is done
through the simple helper macro ``shutdown``:
```clojure
(defactor listener []
  (onReceive [{t :type pi :pi dur :dur}]
    (dispatch-on t
      :approx (do (println (format "\n\tPi approximation: \t\t%1.8f\n\tCalculation time: \t%8d millis"
                                   pi dur))
                (shutdown)))))
```

## Bootstrap the calculation

All that is needed now is to create the actor system and start the calculation
by creating the Master actor and sending him the ``compute`` message from the
``main`` method of our program. There is no big surprise here, except for the
fact that as ``!`` does not work outside of an actor, we have to send this
first message by a direct use of Java interop:
```clojure
(defn -main [& args]
  (let [nw (if args (Integer/parseInt (first args)) 4)
        ne 10000 nm 10000
        sys (actor-system "PiSystem")
        lis (spawn listener [] :in sys :name "listener")
        mas (spawn master [nw nm ne lis] :in sys :name "master")]
    (println "Number of workers: " nw)
    (.tell mas (m-compute))
    (.awaitTermination sys)))
```

## Run it from leiningen

You can run the program by typing ``lein run`` at the command prompt from the
root of the project.

Note that you can pass an integer argument, as in ``lein run 3``, to change the
number of working actors from the default 4. This allows you to quickly test
how much the computation time varies with the number of actors (the optimum
will likely be for a number of actors somewhere between one and two times your
number of cores).

## Overriding configuration externally

When you create an ActorSystem, you can choose to associate with it some
configuration, which will be read from the ``application.conf`` file (which
must reside on your classpath). If you do so, whenever you create an actor in
that system, Akka will look into the configuration for an entry corresponding
to the name of the actor you are creating, and any configuration option found
in the configuration file will be applied to the actor (taking precedence over
whatever might be specified in the code should there be any overlap).

See the Akka documentation for more information on configuration.

## Conclusion

So this is how to use the Okku library to simplify interop with Akka. This
tutorial will, however, leave a Clojure programmer pretty much unimpressed by
Akka, as this computation could just as easily have been done with Clojure
agents.

The true strength of Akka comes from the Actor model, which, while pretty good
for parallelism, only truly shines in a distributed context. For plain old
concurrency, Clojure has easier primitives.

Check out the [remote tutorial](https://github.com/gaverhae/okku/blob/master/src/okku/samples/remote/README.md)
to see how to distribute computation with Okku.
