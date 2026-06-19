Troubleshooting
===============

Cannot run program "cargo"
--------------------------

```
IOException: Cannot run program "cargo"
(in directory "/Volumes/Development/brevity/brevity-integration-tests/rust"):
Exec failed, error: 2 (No such file or directory)
```

Killing the Gradle daemon may fix this.

```bash
$ cd ../..
$ ./gradlew --stop
```

Missing wasm32-wasip2 target
----------------------------

If you've already run `rustup target add wasm32-wasip2` and are still getting this error, that means your rust setup is wonky. (Maybe you, I dunno, tried to yolo it like Bridget did with homebrew) 

Fix by wiping out your existing installation and starting over with the instructions [at rust-lang.org](https://rust-lang.org/learn/get-started/).
