# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0]

First release.

### Added

- `LDLogLevelController`, which drives SLF4J log levels from LaunchDarkly
  feature flags. Levels are applied to the real loggers, so every existing
  `LoggerFactory.getLogger(...)` call responds - including loggers captured in
  `static final` fields before the client existed. No call sites change.
- Two flags, matching the conventions the LaunchDarkly React logger established
  so one pair can drive a browser app and a Java service: `console-log-level`
  (number, 0-5) for the application, and `sdk-log-level` (string) for the SDK's
  own logging.
- Backend detection at runtime, covering Logback, Log4j 2 and
  `java.util.logging`. A backend that is on the classpath but not the one SLF4J
  is bound to is correctly treated as unavailable.
- `LogLevelBridge` as a public extension point, for backends the library does
  not know about. Supply one through the builder, or register it as a
  `ServiceLoader` service and detection will prefer it over the built-ins.
- `LDSdkLogging.slf4j()`, which routes the SDK's own logging through SLF4J. The
  Java SDK does not do this by default, and the SDK log level flag has nothing
  to act on until it is configured.
- Change callbacks for both levels. A callback that throws is logged and
  contained, so a misbehaving listener cannot break log level control.

### Notes

- SDK log level changes take effect on a running client with no
  re-initialization. `LoggingConfigurationBuilder.level(...)` would bake a level
  in at construction time; `LDSdkLogging.slf4j()` deliberately avoids that, and
  the SDK then delegates all filtering to SLF4J.
- Bridges receive a library-owned `LogLevel` rather than `org.slf4j.event.Level`,
  so flag value 0 still means `FATAL` when it reaches a backend that has a real
  `FATAL`. It carries no numeric value on purpose: Java logging frameworks number
  their levels inconsistently and some invert the ordering.
- Flag values 0 and 1 produce identical output on all three backends, because no
  SLF4J call site can emit `FATAL`. Only `currentConsoleLogLevel()` tells them
  apart.
- There is intentionally no `none` value for silencing the SDK, even though the
  SDK's own `LDLogLevel` has one. To silence it, configure the backend directly.
- The SDK and every logging backend are `compileOnly` and are not transitive
  dependencies, so this library never forces a version on an application that
  already depends on them.

[1.0.0]: https://github.com/bradbunce/launchdarkly-java-logger/releases/tag/v1.0.0
