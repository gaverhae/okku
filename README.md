# Introduction

Okku is a Clojure wrapper for the Akka library. Akka is an erlang-inspired
Scala library implementing the Actor model for concurrency and distribution.

The Akka library has a Java interface, but using it directly from Clojure is
rather unwieldy as it is mainly based on subclassing, and requires some
juggling with factories.

For explanations on the Actor model itself and how and when to use it, see the
documentation of either Akka or Erlang.

# Usage

The main goal of the Okku library is to make actor creation and management as
painless as possible. Apart from that, Okku strives to be as thin a wrapper as
possible; for example, Okku functions yield ans manipulate unwrapped Akka
objets, and Okku tries to stay conceptually close to the Akka model. This means
that users of Okku should be able to refer directly to the [Akka
documentation](http://akka.io/docs/) for information on how to use Okku.

The main caveat is that Okku assumes that the user is mainly interested in
distribution, as Clojure itself already provides good primitives for local
concurrency.

## Very brief introduction to the Akka actor hierarchy

To enable the construction of self-healing systems, the Akka actor systems
enforces a hierarchical structure. This means that every actor is the child of
another actor, and every actor knows its own children. This in turn allows an
actor to monitor the health of its children, and to react to their failure.

Of course, every actor needing a parent means we have a chicken-and-egg
problem, which Akka solves by creating special actors for you, which do not
have (user-accessible) parents, and which are called Actor Systems. Basically,
an Akka application begins by creating an Actor System, and then tells this
actor system to spawn the required actors for the rest of the computation.
Actors from this first generation of manually-created actors are typically
thought of as the roots of the actor hierarchy within an application.

## Creating an actor with Okku

The first step in creating an actor is to define its behaviour. This is done
through the ``defactor`` macro, which defines a function that yields an
instance of ``akka.actor.Props`` (that could then be passed to ``.actorOf`` to
create an actor from Akka). It is basically a wrapper around ``proxy``, with a
few convenience macros to use frequently accessed actor functionalities.

(In the next release, this will point to the rest of the documentation, which
does not exist yet. In the mean time, most of these features are exhibited in
the two tutorials, [pi](https://github.com/gaverhae/okku-pi) and
[remote](https://github.com/gaverhae/okku-remote).)

The second step is to use the ``spawn`` macro, which takes an "actor" (a
function defined by defactor), its arguments, and a few named arguments to
create the actor. If no ``:in`` argument is passed, the new actor is spawned as
a child to the "current" actor (this call will obviously fail if done from
outside an actor). ``spawn`` is also used to create an actor on a remote
system.

(Again, in the meantime, see the two tutorials for more information.)

# Configuration through configuration file

Configuration through ``application.conf`` is supported by Akka, and thus by
Okku. See the [Akka documentation](http://akka.io/docs/) for details.

One note of interest: the Okku system adds the possibility of changing the
lookup address for a remote actor through configuration, which is not directly
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

## License

Copyright (C) 2012 Gary Verhaegen.

Distributed under the Eclipse Public License, the same as Clojure.
