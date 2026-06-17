import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishBasePlugin
import com.vanniktech.maven.publish.SourcesJar

plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

if (rootProject.name == "brevity-root") {
  plugins.apply("dev.wasmo.brevity-build")
} else {
  // Don't poison the build when included in brevity-build.
  layout.buildDirectory = File(rootDir, "build/brevity-gradle-plugin")
}

dependencies {
  add("compileOnly", kotlin("gradle-plugin"))
  add("compileOnly", kotlin("gradle-plugin-api"))
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.kotlinpoet)
  implementation(libs.okio)
}

gradlePlugin {
  plugins {
    create("brevity") {
      id = "dev.wasmo.brevity"
      implementationClass = "dev.wasmo.brevity.gradle.BrevityPlugin"
    }
  }
}

project.plugins.withType<MavenPublishBasePlugin> {
  project.extensions.configure<MavenPublishBaseExtension> {
    configure(
      GradlePlugin(
        JavadocJar.Dokka("dokkaGenerateHtml"),
        SourcesJar.Sources(),
      ),
    )
  }
}

