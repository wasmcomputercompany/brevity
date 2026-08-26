Releasing
=========

1. Update `CHANGELOG.md`.

2. Set versions:

    ```
    export RELEASE_VERSION=X.Y.Z
    export NEXT_VERSION=X.Y.Z-SNAPSHOT
    ```

3. Update versions, tag the release, and prepare for the next release.

    ```
    sed -i "" \
      "s/brevity.version=.*/brevity.version=$RELEASE_VERSION/g" \
      gradle.properties

    git commit -am "Prepare for release $RELEASE_VERSION."
    git tag -a $RELEASE_VERSION -m "Version $RELEASE_VERSION"

    sed -i "" \
      "s/brevity.version=.*/brevity.version=$NEXT_VERSION/g" \
      gradle.properties
    git commit -am "Prepare next development version."

    git push && git push --tags
    ```

4. Wait for the [Brevity Publish] pipeline to run.

[Brevity Publish]: https://buildkite.com/wasmo/brevity-publish
