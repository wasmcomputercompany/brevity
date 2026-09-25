plugins {
  alias(libs.plugins.kotlin.jvm)
  id("dev.wasmo.brevity")
  id("dev.wasmo.brevity-build")
}


brevity {
  customTypeMappings.put("wasmo:uuid/types.uuid@0.1.0", "kotlin.uuid.Uuid")
  customTypeMappings.put("wasi:clocks/wall-clock.datetime@0.2.0", "kotlin.time.Instant")
  worlds.add("wasmo:platform/wasmo")

  publish {
    isWorkspace = true
    config = project.layout.projectDirectory.file("config.toml")
  }
}

project.tasks.named("test") {
  dependsOn(project.tasks.named("publishWit"))
}

dependencies {
  implementation(projects.brevity)
  implementation(libs.okio)
}
