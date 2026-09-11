# LaunchDarkly Java Logger

A Java logging utility that uses LaunchDarkly feature flags to control SLF4J log levels in real-time.

Works with Logback, Log4j 2 and `java.util.logging` out of the box, and with
anything else through a small extension point.

## What It Does

This utility provides dynamic log level control through LaunchDarkly feature flags:

1. Application Logging (0-5):
   ```java
   log.error("API error");           // Level 1
   log.warn("Deprecated usage");     // Level 2
   log.info("User logged in");       // Level 3
   log.debug("API response");        // Level 4
   log.trace("Function called");     // Level 5
   ```
   Setting the flag to a number enables that level and every more severe level.
   Example: Setting to 3 (INFO) shows ERROR, WARN, and INFO logs.

   | Flag value | `ConsoleLogLevel` | SLF4J level |
   | --- | --- | --- |
   | 0 | `FATAL` | `ERROR` |
   | 1 | `ERROR` | `ERROR` |
   | 2 | `WARN` | `WARN` |
   | 3 | `INFO` | `INFO` |
   | 4 | `DEBUG` | `DEBUG` |
   | 5 | `TRACE` | `TRACE` |

   SLF4J has no `FATAL` level, so flag values 0 and 1 both apply `ERROR`. The
   distinction is preserved so that a flag shared with the React logger stays
   valid and `currentConsoleLogLevel()` still reports which value is live.

   The column above is the SLF4J level. You never need to care which logging
   framework a consumer runs - the library translates these same 0-5 values into
   Logback, Log4j 2 or `java.util.logging` levels itself. See
   [Level mapping](#level-mapping) for the per-framework table.

2. SDK Logging:
   - Controls LaunchDarkly's internal logging
   - Values: `error`, `warn`, `info`, `debug`
   - Useful for debugging flag evaluation issues

   There is intentionally no `none` value for silencing the SDK, even though the
   SDK's own `LDLogLevel` has one: these four are exactly what the React logger
   documents, which keeps a single flag portable across both. To silence the SDK,
   configure the backend directly - `<logger name="com.launchdarkly" level="OFF"/>`
   for Logback - rather than through a flag.

Unlike the React logger, this library gives you **no new logging API**. It sets
the level of the real loggers in your application, so every existing
`LoggerFactory.getLogger(...)` call responds without any code changes — including
loggers captured in `static final` fields long before the flag changed.

## Prerequisites

1. LaunchDarkly Setup:
   - A LaunchDarkly account
   - The LaunchDarkly Java Server SDK (`com.launchdarkly:launchdarkly-java-server-sdk`) installed and configured
   - An `LDClient` your application already builds and holds

2. A Logging Backend bound to SLF4J - any one of:
   - `ch.qos.logback:logback-classic`
   - `org.apache.logging.log4j:log4j-slf4j2-impl` together with `log4j-core`
   - `org.slf4j:slf4j-jdk14`, for `java.util.logging`
   - or your own `LogLevelBridge`, for anything else

3. Create Two Feature Flags:
   ```text
   // 1. Console Log Level Flag
   {
     key: 'console-log-level',
     type: 'number',
     values: 0-5  // FATAL=0, ERROR=1, WARN=2, INFO=3, DEBUG=4, TRACE=5
   }

   // 2. SDK Log Level Flag
   {
     key: 'sdk-log-level',
     type: 'string',
     values: ['error', 'warn', 'info', 'debug']
   }
   ```

   These are the same two flags the React logger uses, so a single pair can drive
   both a browser app and a Java service.

   Three things are easy to get wrong when setting these up in the LaunchDarkly
   UI:

   - **The types are not interchangeable.** The console flag is read with
     `intVariation` and the SDK flag with `stringVariation`, and neither coerces.
     A console flag whose variations are the *strings* `"0"`-`"5"` falls back to
     your default and logs `is not a number`; numeric variations on the SDK flag
     log `is not one of error/warn/info/debug`.
   - **Leave the values as the bare numbers `0`-`5`, but name the variations.** A
     variation's name and description are UI-only metadata - naming one
     `3 - INFO` does not change what the SDK returns, which is still `3`. Naming
     them is worth doing, because a list of bare integers tells a reader nothing:

     | Value | Suggested variation name |
     | --- | --- |
     | `0` | 0 - FATAL |
     | `1` | 1 - ERROR |
     | `2` | 2 - WARN |
     | `3` | 3 - INFO |
     | `4` | 4 - DEBUG |
     | `5` | 5 - TRACE |

   - **The value is a threshold, not a selection.** Setting the flag to `3`
     enables INFO *and* every more severe level, so `0` is the quietest setting
     and `5` the loudest. That is the opposite of what "level 0" tends to
     suggest, so it is worth saying in the flag's own description field.

## Implementation

1. Install:

   ```kotlin
   // Gradle (build.gradle.kts)
   dependencies {
       implementation("io.github.bradbunce:launchdarkly-java-logger:1.0.0")

       // You supply these two — see Requirements below
       implementation("com.launchdarkly:launchdarkly-java-server-sdk:7.16.0")

       // Any supported backend. Logback shown here; Log4j 2 and
       // java.util.logging work equally well.
       runtimeOnly("ch.qos.logback:logback-classic:1.6.3")
   }
   ```

   ```xml
   <!-- Maven (pom.xml) -->
   <dependency>
     <groupId>io.github.bradbunce</groupId>
     <artifactId>launchdarkly-java-logger</artifactId>
     <version>1.0.0</version>
   </dependency>
   ```

2. Route the SDK's own logs through SLF4J:

   The Java SDK does **not** use SLF4J by default — `Logs.basic()` writes straight
   to the console at `INFO` and does no auto-detection. Until you configure the
   adapter below, the SDK log level flag has nothing to act on.

   ```java
   import com.launchdarkly.sdk.server.LDClient;
   import com.launchdarkly.sdk.server.LDConfig;
   import io.github.bradbunce.ldlogger.LDSdkLogging;

   LDConfig config = new LDConfig.Builder()
       .logging(LDSdkLogging.slf4j())
       .build();

   LDClient client = new LDClient(sdkKey, config);
   ```

   Only the SDK log level flag needs this. The application log level flag works
   regardless of how the SDK logs.

   **No client re-initialization is needed when the SDK log level flag changes.**
   That is worth stating explicitly, because the SDK *can* bake a log level into
   a client at construction time and this library deliberately avoids that path:

   - `LoggingConfigurationBuilder.level(LDLogLevel)` is applied once, when the
     client is built, by wrapping the adapter in a level filter. A level set that
     way is fixed for the life of the client and really would need a re-init to
     change. Do not use it.
   - `LDSdkLogging.slf4j()` instead hands the SDK an adapter that implements
     `LDLogAdapter.IsConfiguredExternally`. The SDK responds by skipping its own
     level filter entirely and delegating all filtering to SLF4J. Combined with
     `.level(...)` the two cancel out: the level is silently ignored.

   So this library never touches the SDK's log configuration at all. It sets the
   level of the SLF4J logger named `com.launchdarkly`, which the SDK's output
   flows through, and the running client picks that up immediately - in both
   directions, quieter or more verbose. `SdkLogLevelIntegrationTest` proves this
   against a real `LDClient`, including that raising the level to `debug` on an
   already-started client produces output it was previously suppressing.

3. Create the controller:

   ```java
   import com.launchdarkly.sdk.LDContext;
   import io.github.bradbunce.ldlogger.LDLogLevelController;

   // Log levels are resolved against one context representing the service or
   // instance, so targeting rules can vary the level by environment or region.
   LDContext serverContext = LDContext.builder("checkout-service")
       .kind("service")
       .set("env", "production")
       .build();

   LDLogLevelController logLevels = LDLogLevelController
       .builder(client, serverContext)
       .consoleLogFlagKey("console-log-level")
       .sdkLogFlagKey("sdk-log-level")
       .build();

   logLevels.start();
   ```

   `start()` evaluates both flags, applies the resulting levels, and subscribes to
   further changes so later flag updates take effect immediately. Calling it more
   than once has no additional effect.

4. Log as you already do — nothing here is logger-specific:

   ```java
   import org.slf4j.Logger;
   import org.slf4j.LoggerFactory;

   public class CheckoutService {
       private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

       public void checkout(String orderId) {
           log.debug("Starting checkout for {}", orderId);
       }
   }
   ```

## Usage

### Targeting specific loggers

By default the application level is applied to the **root logger**, so the whole
application is affected, and the SDK level to `com.launchdarkly`. Narrow either
one if you would rather not touch third-party logging:

```java
LDLogLevelController.builder(client, serverContext)
    .consoleLogFlagKey("console-log-level")
    .applicationLoggerName("com.example.checkout")
    .sdkLogFlagKey("sdk-log-level")
    .sdkLoggerName("com.example.vendor.launchdarkly")
    .build();
```

### Choosing the fallback levels

If a flag is missing, unreachable, or set to a value outside its range, the
controller logs a warning and applies the configured default instead of failing.
Both default to `ERROR`:

```java
LDLogLevelController.builder(client, serverContext)
    .consoleLogFlagKey("console-log-level")
    .defaultConsoleLogLevel(ConsoleLogLevel.WARN)
    .sdkLogFlagKey("sdk-log-level")
    .defaultSdkLogLevel(SdkLogLevel.ERROR)
    .build();
```

Either flag key may be omitted, but not both — a controller with neither would do
nothing, so `build()` rejects it.

### Reacting to changes

```java
LDLogLevelController.builder(client, serverContext)
    .consoleLogFlagKey("console-log-level")
    .onConsoleLogLevelChange(level ->
        metrics.gauge("log.level", level.flagValue()))
    .onSdkLogLevelChange(level ->
        System.out.println("SDK log level changed to: " + level.flagValue()))
    .build();
```

Callbacks fire on `start()` and on every subsequent change, including a fallback
to the default. A callback that throws is logged and swallowed — a misbehaving
listener never breaks log level control.

### Reading the current level

```java
ConsoleLogLevel appLevel = logLevels.currentConsoleLogLevel();
Optional<SdkLogLevel> sdkLevel = logLevels.currentSdkLogLevel();  // empty if no SDK flag
```

### Shutting down

`LDLogLevelController` is `AutoCloseable`. Closing it unsubscribes from flag
changes and leaves the levels it already applied in place:

```java
try (LDLogLevelController logLevels = LDLogLevelController
        .builder(client, serverContext)
        .consoleLogFlagKey("console-log-level")
        .build()) {
    logLevels.start();
    runApplication();
}
```

A closed controller can be started again. Instances are thread-safe, so starting
from application bootstrap while the SDK's event thread delivers flag changes is
safe.

### When no logging backend is bound

If SLF4J has no supported backend bound, the controller logs one warning at
`start()` and stays inactive. It evaluates no flags and subscribes to nothing, so
an application that never added a backend keeps running with whatever logging it
has:

```
LaunchDarkly log level control is inactive: no supported SLF4J backend is bound.
Add ch.qos.logback:logback-classic, org.apache.logging.log4j:log4j-slf4j2-impl
with log4j-core, or org.slf4j:slf4j-jdk14 to the runtime classpath - or supply
your own LogLevelBridge.
```

A backend that is on the classpath but *not* the one SLF4J is bound to also
counts as unavailable. This matters more than it sounds: `log4j-core` in
particular is often pulled in transitively by a dependency in an application that
logs through Logback. Mutating it would change nothing while appearing to work,
so the bridge refuses.

### Supporting another backend

`LogLevelBridge` is the extension point — three methods, no dependency on
anything in this library beyond `LogLevel`:

```java
public final class MyBackendBridge implements LogLevelBridge {

    @Override
    public void setLevel(String loggerName, LogLevel level) {
        // Apply it. loggerName uses SLF4J's convention, so the root logger is
        // named Logger.ROOT_LOGGER_NAME ("ROOT"), not "" as some backends use.
    }

    @Override
    public boolean isAvailable() {
        // Report false unless your backend is the one SLF4J is bound to.
        return true;
    }

    @Override
    public String backendName() {
        return "My Backend";
    }
}
```

Then either pass it in:

```java
LDLogLevelController.builder(client, serverContext)
    .consoleLogFlagKey("console-log-level")
    .logLevelBridge(new MyBackendBridge())
    .build();
```

or register it as a `java.util.ServiceLoader` service for
`io.github.bradbunce.ldlogger.LogLevelBridge`, in which case `detect()` finds it
automatically and prefers it over the built-in bridges.

Note that bridges receive `LogLevel`, not SLF4J's `Level`. That is deliberate:
`Level` has no `FATAL`, so passing it would discard the difference between flag
values 0 and 1 before your bridge ever saw it.

## API

| Export | Description |
| --- | --- |
| `LDLogLevelController` | The controller. `LDLogLevelController.builder(client, context)` |
| `LDLogLevelController.start()` | Evaluates the flags, applies the levels, subscribes to changes |
| `LDLogLevelController.close()` | Unsubscribes; applied levels are left as they are |
| `LDLogLevelController.currentConsoleLogLevel()` | The application level in effect |
| `LDLogLevelController.currentSdkLogLevel()` | The SDK level in effect, or empty if no SDK flag is configured |
| `LDLogLevelController.bridge()` | The backend that level changes are applied through |
| `ConsoleLogLevel` | Enum of application log levels (`FATAL`=0 … `TRACE`=5), with `flagValue()`, `slf4jLevel()` and `fromFlagValue(int)` |
| `SdkLogLevel` | Enum of SDK log levels (`ERROR`, `WARN`, `INFO`, `DEBUG`), with `flagValue()`, `slf4jLevel()` and `fromFlagValue(String)` |
| `LDSdkLogging.slf4j()` | Logging configuration for `LDConfig.Builder.logging` that sends SDK logs to SLF4J |
| `LogLevel` | Backend-independent level (`FATAL` … `TRACE`) that bridges receive, with `slf4jLevel()` |
| `LogLevelBridge` | Backend abstraction and extension point. `LogLevelBridge.detect()` finds the bound backend |
| `LogbackLogLevelBridge` | Logback implementation, via Logback's `LoggerContext` |
| `Log4j2LogLevelBridge` | Log4j 2 implementation, via Log4j 2's `Configurator` |
| `JulLogLevelBridge` | `java.util.logging` implementation. Needs no dependency |

### Builder options

| Method | Default | Description |
| --- | --- | --- |
| `consoleLogFlagKey(String)` | none | The numeric flag (0-5) controlling the application log level |
| `sdkLogFlagKey(String)` | none | The string flag controlling the SDK's own log level |
| `applicationLoggerName(String)` | root logger | Which logger the application level is applied to |
| `sdkLoggerName(String)` | `com.launchdarkly` | Which logger the SDK level is applied to |
| `defaultConsoleLogLevel(ConsoleLogLevel)` | `ERROR` | Level used when the console flag is missing or invalid |
| `defaultSdkLogLevel(SdkLogLevel)` | `ERROR` | Level used when the SDK flag is missing or invalid |
| `onConsoleLogLevelChange(Consumer<ConsoleLogLevel>)` | none | Called whenever the application level changes |
| `onSdkLogLevelChange(Consumer<SdkLogLevel>)` | none | Called whenever the SDK level changes |
| `logLevelBridge(LogLevelBridge)` | auto-detected | Overrides the backend. Use for an unsupported backend, or to inject a fake in tests |

## Benefits

1. Dynamic Control:
   - Change log levels without deploying or restarting
   - Perfect for debugging production issues
   - No code changes needed

2. Zero Call-Site Impact:
   - No wrapper logger to adopt and no imports to change
   - Existing `LoggerFactory.getLogger(...)` calls respond immediately
   - Works for library and framework loggers you do not own

3. Backend Independence:
   - Logback, Log4j 2 and `java.util.logging` supported out of the box
   - The bound backend is detected at runtime, not configured
   - Anything else needs one small class

4. Fails Safe:
   - Invalid or missing flag values fall back to a configured default
   - A missing logging backend produces one warning, not a crash
   - A throwing change callback cannot break level control

## Requirements

- Java 21 or later (the published jar is class file version 65)
- `com.launchdarkly:launchdarkly-java-server-sdk` ≥ 7.16.0
- One supported logging backend bound to SLF4J:

  | Backend | Runtime dependency | Notes |
  | --- | --- | --- |
  | Logback | `ch.qos.logback:logback-classic` | |
  | Log4j 2 | `log4j-slf4j2-impl` + `log4j-core` | `log4j-api` alone is not enough; `Configurator` lives in core |
  | `java.util.logging` | `org.slf4j:slf4j-jdk14` | JUL itself ships with the JDK |

- `org.slf4j:slf4j-api` ≥ 2.0.19 (pulled in transitively; SLF4J's `Level` type
  appears in this library's public API)

The SDK and every backend are declared `compileOnly` here and are **not**
transitive dependencies, the same way the React logger treats its peer
dependencies: an application that already depends on the LaunchDarkly SDK or a
logging backend should never have a version forced on it by a logging helper. You
supply them.

SLF4J is a facade and deliberately offers no way to change levels at runtime, so
each backend needs its own `LogLevelBridge`. Backends that expose no runtime
level API at all — slf4j-simple, slf4j-nop — cannot be supported, and report
themselves unavailable rather than pretending.

### Level mapping

The same 0-5 flag values work for every consumer regardless of their logging
framework - translating them is this library's job, not the flag's. You never
need a per-framework flag or a per-framework variation set.

Backends disagree about which levels exist, so the mapping is lossy at the edges:

| `LogLevel` | Logback | Log4j 2 | `java.util.logging` |
| --- | --- | --- | --- |
| `FATAL` | `ERROR` | `ERROR` | `SEVERE` |
| `ERROR` | `ERROR` | `ERROR` | `SEVERE` |
| `WARN` | `WARN` | `WARN` | `WARNING` |
| `INFO` | `INFO` | `INFO` | `INFO` |
| `DEBUG` | `DEBUG` | `DEBUG` | `FINE` |
| `TRACE` | `TRACE` | `TRACE` | `FINEST` |

Every column above is asserted against a real binding of that framework, in
`LogbackLogLevelBridgeTest`, `Log4j2BackendTest` and `JulBackendTest`
respectively - not inferred from documentation. Two consequences of the table are
worth knowing because they look like bugs from the LaunchDarkly UI:

- **Flag values 0 and 1 produce identical output on all three backends**, since
  no SLF4J call site can emit `FATAL`. Only `currentConsoleLogLevel()` tells them
  apart.
- **`java.util.logging` never produces `CONFIG` or `FINER`**, because no flag
  value maps onto them.

Log4j 2 is the interesting one: it *has* a real `FATAL` level, but SLF4J call
sites cannot emit `FATAL`, so a logger pinned there would discard even
`log.error(...)` and silence the application. `FATAL` therefore maps to `ERROR`,
matching what Logback and JUL can express. If you log through the Log4j 2 API
directly and want true `FATAL`, supply your own bridge.

## Publishing

Releases are cut by pushing a `v*` tag whose version matches `gradle.properties`;
the workflow refuses a tag that disagrees. It builds and tests first, including
the coverage gate, then publishes.

**GitHub Packages** needs no setup - the workflow's `GITHUB_TOKEN` is enough.
Note that GitHub Packages requires authentication even for public artifacts, so
consumers need a token to resolve from it.

**Maven Central** needs four repository secrets, and is skipped with a notice if
`CENTRAL_TOKEN_USERNAME` is absent:

| Secret | What it is |
| --- | --- |
| `CENTRAL_TOKEN_USERNAME` | Central Portal user token username |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal user token password |
| `SIGNING_KEY` | ASCII-armoured GPG private key. Central requires signed artifacts |
| `SIGNING_PASSWORD` | Passphrase for that key |

It also needs the `io.github.bradbunce` namespace verified in the Central Portal, a
one-time DNS TXT record on `bradbunce.dev`.

Sonatype has no first-party Gradle plugin for the Central Portal, and OSSRH
itself reached end of life in June 2025. Rather than take on a third-party
publishing plugin, the build deploys to Sonatype's Nexus-2-compatible staging
endpoint, which plain `maven-publish` can target. A deployment uploaded that way
stays invisible in the Portal until promoted, so the workflow makes the
follow-up promotion call itself. The final publish confirmation in the Portal
remains a deliberate manual step.

## Development

```bash
./gradlew build              # compile, run all three test suites, build jar/sources/javadoc
./gradlew jacocoTestReport   # coverage across all three suites
```

`check` runs five separate test tasks. A single classpath can only ever have one
SLF4J backend bound, so proving that detection picks the right bridge requires
one source set per scenario:

| Task | Classpath | Covers |
| --- | --- | --- |
| `test` | Logback bound | Everything reachable from a normally configured application, plus every bridge's "wrong backend" guard |
| `log4j2BackendTest` | Log4j 2 bound | Detection and level mapping against a real Log4j 2 configuration |
| `julBackendTest` | `java.util.logging` bound | Detection, JUL's level names, and the handler widening a finer level needs |
| `noBackendTest` | slf4j-api only | The degraded path when no backend was ever added, plus that the backend bridges still *load* when their backend is absent |
| `mismatchedBackendTest` | Logback and Log4j 2 present, SLF4J bound elsewhere | The refusal to mutate a backend that nothing logs through |

The last task forces the binding with `-Dslf4j.provider=...` rather than relying
on service-loader order, which would otherwise make "which backend won"
non-deterministic. Together the five cover 100% of instructions, branches, lines
and methods.

The build compiles with `-Xlint:all -Werror` and pins a Java 21 toolchain, so it
does not depend on whichever JDK happens to be on `PATH`.

`check` also enforces 100% instruction and branch coverage through
`jacocoTestCoverageVerification`, so an uncovered branch fails the build rather
than quietly eroding the number.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License
MIT
