Change Log
==========

## Version 0.6.0

_2026-11-01_

 * New: Implement interface and resource functions.
 * New: Implement import calls.
 * New: Implement async calls.
 * New: Implement the `map` type.
 * New: Honor `__init_stack_pointer` and `__init_tls_base` globals.
 * Fix: Fail when keywords are used as identifiers.
 * Fix: Correctly implement alignment when encoding types.


## Version 0.5.0

_2026-10-06_

 * Fix: Allocate memory consistently when lifting and lowering parameters.
 * New: Publish `.wit` files to a registry.


## Version 0.4.0

_2026-09-16_

 * New: Support type aliases and flags.
 * Breaking: Change APIs to configure the Brevity Gradle plugin.
 * New: Support downloading `.wit` files from an OCI registry. This requires [wkg] on the host
   computer's `$PATH`.
 * New: `WitAdapter` enables mapping WIT-generated types to Kotlin types.


## Version 0.3.0

_2026-09-02_

 * New: Promote type declarations inside of `types` interfaces to the top-level.
 * Fix: Better detection and reporting of malformed identifiers.
 * Fix: Ignore preceding `%` signs on identifiers.
 * Fix: Don't put underscores in package names.


## Version 0.2.0

_2026-08-28_

 * Fix: Support passing allocated parameters from host to guest.
 * Breaking: Change how WASI packages are organized.
 * Breaking: Use ByteString as the encoded byte for `list<s8>` and `list<u8>`.
 * New: Better context on issues reported in `.wit` files.
 * Fix: Retain `@WasmoExport`-annotated functions, when they are not in the application module.


## Version 0.1.1

_2026-08-26_

 * Fix: Depend on the published artifact when running the Brevity Gradle plugin.


## Version 0.1.0

_2026-08-26_

Initial release.


[wkg]: https://github.com/bytecodealliance/wasm-pkg-tools
