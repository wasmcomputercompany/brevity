Rust
====

Brevity uses Rust as a reference implementation of WIT and WebAssembly.

We currently use a beta release `1.100.0-beta.7` because that's required for WASIp3.

Rust
----

The Rust folks recommend installing via [rustup] and not homebrew.

Configure Brevity’s default Rust version:

```bash
$ rustup install 1.100.0-beta.7
$ rustup override set 1.100.0-beta.7-aarch64-apple-darwin
```

Debug Rust with `cargo-expand`.

```bash
$ cargo install cargo-expand
```

```bash
$ cargo expand
```

Web Assembly Target
-------------------

Once you've set up `rustup`, add a target to build `.wasm` files.

```bash
$ rustup target add wasm32-wasip3
```

wasm-tools
----------

You'll need [wasm-tools] to process generated `.wasm` files.

```bash
$ cargo install wasm-tools
```

wkg
---
Brevity requires [wkg] to fetch WASI `.wit` files.

```bash
$ cargo install wkg
```

[wkg]: https://github.com/bytecodealliance/wasm-pkg-tools

[rustup]: https://rust-lang.org/tools/install/

[wasm-tools]: https://github.com/bytecodealliance/wasm-tools
