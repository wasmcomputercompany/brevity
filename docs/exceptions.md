Exceptions
----------

Some WIT canonical functions like `Stream.send()` are defined to _trap_ if they're invoked at the
wrong time, or with illegal arguments.

Our implementations of these functions throws an `IllegalStateException`, but that exception doesn't
yet cause the Wasm engine to permanently exit. Instead, from the call site it appears to throw
normally.

This is a bug. We need to fix calls from guest to host to catch exceptions and properly tear down
the engine.
