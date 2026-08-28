rootProject.name = "brevity-root"

includeBuild("brevity-build")

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

include(":brevity")
include(":brevity-cli")
include(":brevity-gradle-plugin")
include(":brevity-integration-tests")
include(":brevity-kotlin-generator")
include(":brevity-testing")
include(":brevity-wit")
include(":wasi:brevity-wasi")
include(":wasi:brevity-wasi-p1")
include(":wasi:brevity-wasi-p2")
include(":wasi:brevity-wasi-p3")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
