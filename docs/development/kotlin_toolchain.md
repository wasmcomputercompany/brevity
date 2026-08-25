The Kotlin Toolchain
====================

This project builds with Gradle, but its test suite also uses [The Kotlin Toolchain] to build
generated test projects.

We use version 0.12.0.

Install it like this:

```bash
$ export KOTLIN_CLI_VERSION="0.12.0"
$ curl -fsSL https://kotl.in/install.sh | sh
$ cd ~/.local/bin
$ kotlin update --target-version "$KOTLIN_CLI_VERSION" --create
$ kotlin --version
```

[The Kotlin Toolchain]: https://kotlin-toolchain.org/latest/
