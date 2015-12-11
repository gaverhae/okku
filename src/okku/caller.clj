(ns okku.caller
  "Unified api for making synchronous and asynchronous requests.")


(defprotocol Caller
  "A protocol for sending messages synchronously or asynchronously."
  (-tell [receiver args]
    "Asyncronous fire and forget protocol.  This API returns nil.")

  (-tell! [receiver args]
    "Synchronous send-and-wait protocol.  Depending on the receiver, (e.g.: remote)
    synchronous behavior may not be possible, in which case this is synonymous
    with asynchronous -tell.")

  (-ask [receiver args]
    "Send a message to a receiver, passing args, and return the result
    in a Future or Promise.")

  (-ask! [receiver args]
    "Send a message to a receiver, passing args, and wait for the result."))


(defn tell
  "Send a message to a receiver, passing args.  This API returns nil.  This function
delegates to implementations of the Caller protocol, so it is extensible by
implementing Caller over additional types."
  [receiver & args]
  (-tell receiver args))

(def !
  "Send a message to a receiver, passing args.  This API returns nil.  This function
delegates to implementations of the Caller protocol, so it is extensible by
implementing Caller over additional types."
  tell)

(defn tell!
  "Synchronous send-and-wait protocol.  Depending on the receiver, (e.g.: remote)
synchronous behavior may not be possible, in which case this is synonymous
with asynchronous -tell."
  [receiver args]
  (-tell! receiver args))

(def !!
  "Synchronous send-and-wait protocol.  Depending on the receiver, (e.g.: remote)
synchronous behavior may not be possible, in which case this is synonymous
with asynchronous -tell."
  tell!)

(defn ask
  "Send a message to a receiver, passing args, and return a future or promise
that will contain the result.  This function delegates to implementations of
the Caller protocol so it is extensible by implementing Caller over additional types."
  [receiver & args]
  (-ask receiver args))

(def ?
  "Send a message to a receiver, passing args, and return a future or promise
that will contain the result.  This function delegates to implementations of
the Caller protocol so it is extensible by implementing Caller over additional types."
  ask)

(defn ask!
  "Send a message to a receiver, passing args, and wait for the result."
  [receiver & args]
  (-ask! receiver args))

(def ??
  "Send a message to a receiver, passing args, and wait for the result."
  ask!)
