(ns okku.caller
  "Unified api for making synchronous and asynchronous requests.")


(defprotocol Caller
  "A protocol for sending messages synchronously or asynchronously."
  (-tell [receiver args]
    "Asyncronous fire and forget protocol.  This API returns nil.")

  (-reply [this args]
    "When used in a message handler, reply to this message's sender.")

  (-ask [receiver args]
  "Send a message to a receiver, passing args, and return a result.  Depending
on context, the result may be a future/promise.  Consult the implementation's
API docs for details."))


(defn tell
  "Send a message to a receiver, passing args.  This API returns nil.  This function
delegates to implementations of the Caller protocol, so it is extensible by
implementing Caller over additional types."
  [receiver & args]
  (-tell receiver args))

(defmacro !
  "Send a message to a receiver, passing args.  This API returns nil.  This function
delegates to implementations of the Caller protocol, so it is extensible by
implementing Caller over additional types."
  [receiver & args]
  `(okku.caller/tell ~receiver ~@args))


(defn ask
  "Send a message to a receiver, passing args, and return a result.  Depending
on the context, the result may be a future/promise.   This function
delegates to implementations of the Caller protocol so it is extensible by
implementing Caller over additional types."
  [receiver & args]
  (-ask receiver args))

(defmacro ?
  "Send a message to a receiver, passing args, and return a result.  Depending
on the context, the result may be a future/promise.   This function
delegates to implementations of the Caller protocol so it is extensible by
implementing Caller over additional types."
  [receiver & args]
  `(okku.caller/ask ~receiver ~@args))


(defn reply
  "Reply to this message's sender.    This function delegates to implementations
of the Caller protocol, so it is extensible by implementing Caller over additional types."
  [this & args]
  (-reply this args))

(defmacro &
  "Reply to this message's sender.    This function delegates to implementations
of the Caller protocol, so it is extensible by implementing Caller over additional types."
  [this & args]
  `(okku.caller/reply ~this ~@args))


