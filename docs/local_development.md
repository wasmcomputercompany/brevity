Local Development
=================

Brevity requires some third-party tools to build WebAssembly.

Rust
----

The Rust folks recommend installing via [rustup] and not homebrew.

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
$ rustup target add wasm32-wasip2
```

wasm-tools
----------

You'll need [wasm-tools] to process generated `.wasm` files.

```bash
$ cargo binstall wasm-tools
```

[rustup]: https://rust-lang.org/tools/install/

[wasm-tools]: https://github.com/bytecodealliance/wasm-tools
