CI
==

We do CI on [Buildkite].

Docker
------

Our CI image prepares [Docker] with all this project's dependencies:

* JDK
* Gradle
* NodeJS: to run Kotlin/Wasm tests
* Rust: our reference platform for WebAssembly

See the [Dockerfile](../../brevity-build/ci/Dockerfile).


Building & Publishing Images
----------------------------

CI needs `amd64` images even though MacBooks run `aarch64`.

```bash
$ export DOCKER_DEFAULT_PLATFORM=linux/amd64
$ cd ../../brevity-build/brevity-ci
$ docker build -t wasmo/brevity-ci .
$ docker push wasmo/brevity-ci
```

After pushing an image, update the SHA256s so future builds will use this new image:
 * [publish.yml](../../.buildkite/publish.yml)
 * [build.yml](../../.buildkite/build.yml)


Local Execution
---------------

Rebuild the container if necessary.

```bash
$ cd ../..
$ cd brevity-build/brevity-ci
$ docker build -t wasmo/brevity-ci .
$ docker push wasmo/brevity-ci
```

Next run the build:

```bash
$ cd ../..
$ docker run \
  --volume .:/workdir \
  --workdir /workdir \
  wasmo/brevity-ci \
  gradle :jvmTest -Pbrevity.build.environment=ci
```

Consider keeping a separate clone of the Brevity repo for doing Linux x64 builds. Our Gradle plugins
get grumpy if the architecture changes between builds.


[Buildkite]: https://buildkite.com/wasmo/
[Docker]: https://buildkite.com/resources/plugins/buildkite-plugins/docker-buildkite-plugin/
