rootProject.name = "brevity-build"

include(":brevity-gradle-plugin")
project(":brevity-gradle-plugin").projectDir = File("../brevity-gradle-plugin")

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
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
