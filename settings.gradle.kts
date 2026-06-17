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
include(":brevity-gradle-plugin")
include(":brevity-integration-tests")
include(":brevity-kotlin-generator")
include(":brevity-testing")
include(":brevity-wasi")
include(":brevity-wit")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
