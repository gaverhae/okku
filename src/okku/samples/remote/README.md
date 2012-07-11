# Introduction

This tutorial mirrors the [Java remoting
tutorial](http://doc.akka.io/docs/akka/2.0.2/java/remoting.html) from the Akka
documentation. However, the code presented here will not be a direct port of
that tutorial; for pretty direct version, see the ``okku.clj`` file right along
this README. If you want to download the code described in this README, head
over to:
```
https://github.com/gaverhae/okku-remote
```
By the way, the Akka remoting example from Java is available on github at the
following address:
```
https://github.com/akka/akka/tree/master/akka-samples/akka-sample-remote
```


# Setup

Before we can begin writing code, we have to create a new project and set it up
correctly. This is done by using ``lein new okku-remote``, adding the
```clojure
[org.clojure.gaverhae/okku "0.1.0"]
```
dependency to the ``project.clj``, and adding the bare minimal
``resources/application.conf``
required by Akka to enable remoting:
```
akka.actor.provider = "akka.remote.RemoteActorRefProvider"
akka.remote.transport = "akka.remote.netty.NettyRemoteTransport"
akka.remote.netty {
    hostname = "127.0.0.1"
    port = 2552
}
```

# Looking-up remote actors

To obtain an ActorRef to a remote actor, Okku provides the ``look-up`` macro.
Note that the ActorRef, which is used to send messages to the actor, does not
necessariliy lives in the same ActorSystem as the actual actor it is a
reference to. The look-up macro takes the address of the looked-up actor as a
first, required argument (the syntax of the address is exactly the same as
described in the Akka documentation: it is passed as-is to the Akka objects)
and an optional ``:in`` keyword argument which specifies the ActorSystem in
which the ActorRef must be created.

# Creating actors remotely

To remotely create an actor, you can create it normally in your code, just as
if it was a local actor, and specify in the configuration that it has to be
deployed on another node. If, for example, you have the following line in your
``application.conf``:
```
akka.actor.deployment./serviceA/retrieval.remote = "akka://app@10.0.0.1:2552"
```
then, when you use the ``spawn``macro to create an actor named ``retrieval`` in
the ``serviceA`` ActorSystem, just like it was a local actor:
```clojure
(let [as (actor-system "serviceA")]
  (spawn actor-def [] :in as :name "retrieval"))
```
it will remotely create the actor on the node with IP address 10.0.0.1 in the
``app`` ActorSystem.

For complex setups, you can "namespace" your ``application.conf`` so each of
your actor systems has its own section. When creating an ActorSystem, the
``:config`` optional keyword argument can be supplied, and the ActorSystem will
then only read the configuration options beginning with that string.

## Programmatic remote deloyment

You can also specify where to deploy an actor directly in your code, which can
be useful in a more dynamic setting (for example when the node on which to
deploy an actor has to be computed at runtime). This is done by passing a
``:deploy-at`` keyword argument to the ``spawn`` macro, with the address
at which the actor has to be created. For example, to achieve the same result
as in the previous section:
```
(spawn actor-def [] :in as :name retrieval :deploy-at "akka://app@10.0.0.1:2552")
```

That actor can the be retrieved via ``look-up``:
```clojure
(look-up "akka://app@10.0.0.1:2552/user/serviceA/retrieval")
```


