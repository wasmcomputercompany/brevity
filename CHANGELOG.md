Change Log
==========

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
