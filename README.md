# Leiningen

```clojure
[org.clojure.gaverhae/okku "0.1.4"]
```

Okku is primarily developped and tested on Clojure 1.4. It has been
occasionally tested on 1.2 and 1.3, and it seems to work fine. Do not hesitate
to report any incompatibility.

Okku has recently been updated to rely on Akka `2.3.8`. This has not been
extensively tested; please report any problem.

# Introduction

Okku is a Clojure wrapper for the Akka library. Akka is an erlang-inspired
Scala library implementing the Actor model for concurrency and distribution.

For explanations on the Actor model itself and how and when to use it, see the
documentation of either Akka or Erlang.

# Usage

Okku strives to be as thin a wrapper as possible; for example, Okku functions
yield and manipulate unwrapped Akka objects, and Okku tries to stay
conceptually close to the Akka model. This means that users of Okku should be
able to refer directly to the [Akka documentation](http://akka.io/docs/) for
information on how to use Okku. One only has to keep in mind that the ``actor``
macro yields a ``Props`` object while the ``spawn`` macro is basically a
wrapper around ``.actorOf``.

Example usage of Okku is given in the two tutorials:
[pi](https://github.com/gaverhae/okku-pi) and
[remote](https://github.com/gaverhae/okku-remote). The tutorials are versioned
in sync with the library; you should only use release (i.e. non-SNAPSHOT)
versions of the tutorials.

## Very brief introduction to the Akka actor hierarchy

The Akka actor systems enforces a hierarchical structure. This means that every
actor is the child of another actor, and every actor knows its own children.

Of course, every actor needing a parent means we have a chicken-and-egg
problem, which Akka solves by creating special actors for you, which do not
have (user-accessible) parents, and which are called Actor Systems. Basically,
an Akka application begins by creating an Actor System, and then telling this
Actor System to spawn the required actors for the rest of the computation.
Actors from this first generation of manually-created actors are typically
thought of as the roots of the actor hierarchy within an application.

## Creating an ActorSystem

The creation of an ActorSystem is done through the ``actor-system`` function,
which in its most basic form simply takes a name as a parameter. See the
documentation (``docs`` folder) for details on the possible options.

## Creating an actor with Okku

The first step in creating an actor is to define its behaviour. This is done
through the ``actor`` macro, which yields an ``akka.actor.Props`` object (that
could then be passed to ``.actorOf`` to create an actor from Akka). It is
basically a wrapper around ``proxy``. Okku als defines a few convenience macros
to use frequently accessed actor functionalities, such as ``stop``. See the
[marginalia](https://github.com/fogus/marginalia/)-generated documentation in
the ``docs`` folder for more details.

The second step is to use the ``spawn`` macro, which takes an "actor" (a
``Props`` object as yielded by the ``actor`` macro), and a few named arguments
to create the actor. If no ``:in`` argument is passed, the new actor is spawned
as a child of the "current" actor (which means that the ``:in`` argument is
required if called from outside of an actor, though that can only be detected
at runtime). ``spawn`` is also used to create an actor on a remote system.

With all that said, here is an example code to illustrate the basics, provided
the Okku jar is in your classpath:
```clojure
(use 'okku.core)
(let [as (actor-system "test")
      echo-actor (spawn (actor (onReceive [msg]
                                 (println msg)))
                        :in as)]
  (.tell echo-actor "Testing...")
  (.tell echo-actor ["more" {"complex" "object"}]))
```

One restriction of the Akka model is that messages between actors have to be
immutable objects. This is the default for Clojure values, but it's still
important to bear in mind.

## Configuration through configuration file

Configuration through ``application.conf`` is supported by Akka, and thus by
Okku. See the [Akka documentation](http://akka.io/docs/) for details.

One note of interest: the Okku system adds the possibility of changing the
look-up address for a remote actor through configuration, which is not directly
supported by Akka (though it is not hard to do through accessing the
configuration object, which is exactly what Okku does).

To avoid polluting the ``akka`` "namespace" in the configuration file, Okku
adds an ``okku.lookup`` namespace for actor look-up. Supported configuration
options are:
```
okku.lookup.<actor path> {
  protocol
  actor-system
  hostname
  port
  path
}
```

If the path does not begin with a "/", Okku will automaticall add "/user/" in
front of it.

# License

Copyright (C) 2012 Gary Verhaegen.

Distributed under the Eclipse Public License, the same as Clojure.
