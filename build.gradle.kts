import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishBasePlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

plugins {
  alias(libs.plugins.kotlin.multiplatform).apply(false)
  alias(libs.plugins.kotlin.jvm).apply(false)
  alias(libs.plugins.maven.publish).apply(false)
  id("dev.wasmo.brevity-build")
}

// Non release builds publish with the marker version so the integration tests resolve what this
// build just built from the test repository. Releases pass this flag.
val release = hasProperty("brevity.release")

allprojects {
  group = "dev.wasmo.brevity"
  version = project.property("brevity.version")!!

  plugins.withType<MavenPublishBasePlugin> {
    extensions.configure<PublishingExtension> {
      repositories {
        maven {
          name = "test"
          url = project.rootProject.layout.buildDirectory.dir("localMaven").get().asFile.toURI()
        }
      }
      if (!release) {
        publications.withType<MavenPublication>().configureEach {
          version = "0-testing"
        }
      }
    }
    // Refuse to publish the test repo marker anywhere but a test repository.
    tasks.withType<AbstractPublishToMaven>().configureEach {
      val isRelease = release
      doFirst {
        check(isRelease || name.endsWith("ToTestRepository")) {
          "Attempting to publish `0-testing`, pass -Pbrevity.release=true to publish a real version"
        }
      }
    }
    extensions.configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      if (project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
      }
      pom {
        name = project.name
        description = "WebAssembly WIT and Components in Kotlin"
        inceptionYear = "2026"
        url = "https://wasmo.com/"
        licenses {
          license {
            name = "The Apache License, Version 2.0"
            url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            distribution.set("repo")
          }
        }
        developers {
          developer {
            id = "wasmo-team"
            name = "Wasmo Team"
            url = "https://wasmo.com/"
          }
        }
        scm {
          url = "https://github.com/wasmcomputercompany/brevity/"
          connection = "scm:git:git://github.com/wasmcomputercompany/brevity.git"
          developerConnection = "scm:git:ssh://git@github.com/wasmcomputercompany/brevity.git"
        }
      }
    }
  }

  // Don't download Node in CI, it's available in our Docker image.
  project.plugins.withType<NodeJsPlugin> {
    project.the<NodeJsEnvSpec>().apply {
      if (project.findProperty("brevity.build.environment") == "ci") {
        download = false
        command.set("/usr/local/bin/node")
      }
      version = "26.1.0"
    }
  }
}
