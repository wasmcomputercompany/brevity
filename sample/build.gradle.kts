plugins {
  alias(libs.plugins.kotlin.jvm)
  id("dev.wasmo.brevity")
  id("dev.wasmo.brevity-build")
}


brevity {
  ociPackages.addAll(
    "wasi:cli@0.2.0",
    "wasi:clocks@0.2.0",
    "wasi:filesystem@0.2.0",
    "wasi:http@0.2.0",
    "wasi:io@0.2.0",
    "wasi:random@0.2.0",
    "wasi:sockets@0.2.0",
  )
  customTypeMappings.put("wasmo:uuid/types.uuid@0.1.0", "kotlin.uuid.Uuid")
  customTypeMappings.put("wasi:clocks/wall-clock.datetime@0.2.0", "kotlin.time.Instant")
  worlds.add("wasmo:platform/wasmo")
}

dependencies {
  implementation(projects.brevity)
  implementation(libs.okio)
}
