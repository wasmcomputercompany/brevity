Brevity
=======

> _‘brevity is the soul of wit’_
>  –– Polonius in Shakespeare’s Hamlet

This is a Kotlin implementation of a WIT processor. It compiles WIT specifications into Kotlin
interfaces, as well as host and guest bridging code for the WASM runtime.

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
* **wasi**
  * **brevity-wasi**: Our implementations of the WASI APIs.
  * **brevity-wasi-p1**: A hand-authored host binding for [WASI Preview 1]. We can't use Brevity
    to generate this, because WASI Preview 1 cannot be expressed with `.wit`.
  * **brevity-wasi-p2**: Compiles the [WASI 0.2.0 tag] (Preview 2) to Kotlin.
  * **brevity-wasi-p3**: Compiles the [WASI main branch] (Preview 3) to Kotlin.

Models
------

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
