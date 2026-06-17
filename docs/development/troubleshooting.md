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
