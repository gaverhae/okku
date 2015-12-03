This file documents the changes to the API, not internal or documentation
changes. It is intended for people who already use some version of the library
and want to upgrade to another version.

# v0.1.5 - 2015-11-19

> Thanks to David Orme ([coconutpalm](https://github.com/coconutpalm)) for
> doing just about all of the work that went into this release.

- Update Akka to 2.3.14.
- Update Clojure to 1.7.0.
- Introduce a Clojure wrapper for the `ask` pattern.

# v0.1.4 - 2015-01-18

- Update Akka to 2.3.8. Important notes:
  * The two-arguments form of the `tell` method has been removed from Akka; in
    my (very limited) tests, passing `nil` as the third argument seems to work
    when the receiver does not need to respond. This only affects direct use of
    `tell`, as Okku was always using the three-args version.
  * Akka changed the name of the tcp protocol from `akka://` to `akka.tcp://`.
    The corresponding configuration entries have also been renamed
    (`akka.remote.netty.port` becomes `akka.remote.netty.tcp.port`, etc.)
  * These are the only changes that came up in my own, very limited tests.
    Please carefully read the Akka release notes before upgrading.

# v0.1.3 - 2012-08-03

- Added explicit require of `clojure.string` for Clojure 1.2 & 1.3 compatibility.

# v0.1.2 - 2012-07-24

- Internal refactorings; reduced macro usage. No (intended) user-visible change.

# v0.1.1 - 2012-07-22

- Addition of documentation and tests. No (intended) user-visible change.

# v0.1.0 - 2012-07-20

- Initial release.
