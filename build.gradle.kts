import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishBasePlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

plugins {
  alias(libs.plugins.kotlin.multiplatform).apply(false)
  alias(libs.plugins.kotlin.jvm).apply(false)
  alias(libs.plugins.maven.publish).apply(false)
  id("dev.wasmo.brevity-build").apply(false)
}

allprojects {
  group = "dev.wasmo.brevity"
  version = "0.1.0-SNAPSHOT"

  plugins.withType<MavenPublishBasePlugin> {
    extensions.configure<PublishingExtension> {
      repositories {
        maven {
          name = "test"
          url = project.rootProject.layout.buildDirectory.dir("localMaven").get().asFile.toURI()
        }
      }
    }
    extensions.configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
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
      if (project.findProperty("wasmo.build.environment") == "ci") {
        download = false
        command.set("/usr/local/bin/node")
      }
      version = "26.1.0"
    }
  }
}

