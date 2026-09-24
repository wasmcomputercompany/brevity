Brevity
=======

> _‘brevity is the soul of wit’_
>  –– Polonius in Shakespeare’s Hamlet (blitheringly)

This is a Kotlin implementation of a WIT processor. It compiles WIT specifications into Kotlin
interfaces, as well as host and guest bridging code for the Wasm runtime.

See the [Explainer], [Overview] and [Spec].

This project contains documentation and specifications copyrighted by the
[W3C WebAssembly Community Group], licensed under the Apache license.

* **brevity**: A runtime library for running components in Kotlin. It declares built-in types for
  `Tuple` and `Result` types.
* **brevity-build**: Gradle project so we can run Brevity’s Gradle plugin in Brevity’s own build.
* **brevity-cli**: Command-line executable that compiles `.wit` into `.kt`.
* **brevity-gradle-plugin**: Invokes brevity-cli from Gradle.
* **brevity-integration-tests**: executes generated API stubs on the [Chicory] runtime, using
  both Kotlin and Rust as guest languages.
* **brevity-kotlin-generator**: uses parsed `.wit` files to generate `.kt` files.
* **brevity-testing**: test facets for our own internal testing.
* **brevity-wit**: Parses and models `.wit` files.
* **sample**: Sample project to exercise brevity gradle functionality.
* **wasi**
  * **brevity-wasi**: Our implementations of the WASI APIs.
  * **brevity-wasi-p1**: A hand-authored host binding for [WASI Preview 1]. We can't use Brevity
    to generate this, because WASI Preview 1 cannot be expressed with `.wit`.
  * **brevity-wasi-p2**: Compiles the [WASI 0.2.0 tag] (Preview 2) to Kotlin.
  * **brevity-wasi-p3**: Compiles the [WASI main branch] (Preview 3) to Kotlin.

Features
--------

Brevity generates nice Kotlin APIs from `.wit` source files. It supports Kotlin as the host
(Kotlin/JVM) and the guest (Kotlin/Wasm).

Brevity generates APIs using `okio.ByteString` for `list<s8>` and `list<u8>` inputs.

Brevity flattens nested `types` interfaces. This yields simpler generated code, particularly for
WASI which uses `types` convention extensively.

Brevity can map limited WIT types like `WallClock.Datetime` to preferred platform types like
`kotlin.time.Instant`.

Usage
-----

Add the Brevity plugin to your `build.gradle.kts`:

```kotlin
plugins {
  id("dev.wasmo.brevity")
}
```

Then add the worlds you want to generate APIs for:

```kotlin
brevity {
  worlds.add("wasmo:platform/wasmo")
}
```

WIT source is defined in `commonMain/wit` in a [wkg]-friendly structure. [wkg] is run on `commonMain/wit`
to fetch dependencies as part of the build process. Refer to [wkg] documentation for information on how
this may be configured.

Custom type mappings may be declared in your build file, too:

```kotlin
  customTypeMappings.put("wasi:clocks/wall-clock.datetime@0.2.0", "kotlin.time.Instant")
```

...and then implemented in Kotlin by providing an implementation of the generated Adapters
interface in the same package:

```kotlin
package wit.wasi.clocks.v0_2_0

import dev.wasmo.brevity.WitAdapter
import kotlin.time.Instant

internal object RealAdapters : Adapters {
  override val wallClockDatetime = object : WitAdapter<WallClock.Datetime, Instant> {
    override fun fromWit(wit: WallClock.Datetime) =
      Instant.fromEpochSeconds(wit.seconds.toLong(), wit.nanoseconds.toInt())

    override fun toWit(value: Instant) =
      WallClock.Datetime(value.epochSeconds.toULong(), value.nanosecondsOfSecond.toUInt())
  }
}
```

If you need to generate a Kotlin interface for a world defined in an existing WASM component
(as is done in `:wasi:brevity-wasi-p3`), manually add the source packages and all their dependencies
to brevity's build source in your gradle file:

```kotlin
brevity {
  ociPackages.addAll(
    "wasi:cli@0.3.1",
    "wasi:clocks@0.3.1",
    "wasi:filesystem@0.3.1",
    "wasi:http@0.3.1",
    "wasi:random@0.3.1",
    "wasi:sockets@0.3.1",
  )
  worlds.add("wasi:http/service@0.3.1")
}
```

Publishing Wit APIs
-------------------

Add a `publish` section to publish wit APIs through gradle:

```kotlin
brevity {
  publish {
    // Required - true if your wit folder is a single package layout, false if it
    // is a whole workspace of packages.
    workplace = true
    // Optional - override wkg's config.toml file. Any paths defined here are resolved relative
    // to the working dir where wkg is invoked, `build/brevity/sourceWit`.
    config = "path/to/config/file"
  }
}
```

Implementation
--------------

### Models

We have several different representations of the `.wit` code, that fit together in a pipeline.

* **io**: a direct representation of the `.wit` source code. This isn’t linked and so type
  references are just strings and not resolved. Use `IoWitPackageReader` to load this model.
* **ir**: a linked representation of an entire project. Type references are resolved to their
  fully-qualified values. Includes are applied, so worlds contain their full transitive
  dependencies. This representation doesn’t model syntactic sugar like `Use` and `Include`. Use
  `IrMapper` to transform `io` into this model.
* **api**: a user-facing Kotlin API for a project, represented as `KotlinPoet` files.


[Chicory]: https://github.com/dylibso/chicory
[Explainer]: https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md
[Overview]: https://component-model.bytecodealliance.org/design/wit.html
[Spec]: https://github.com/WebAssembly/component-model/blob/main/design/mvp/WIT.md
[W3C WebAssembly Community Group]: https://www.w3.org/community/webassembly/
[WASI 0.2.0 tag]: https://github.com/WebAssembly/WASI/tree/v0.2.0
[WASI Preview 1]: https://github.com/WebAssembly/WASI/tree/wasi-0.1
[WASI main branch]: https://github.com/WebAssembly/WASI/
[wkg]: https://github.com/bytecodealliance/wasm-pkg-tools
