plugins {
  `kotlin-dsl`
  alias(libs.plugins.dokka).apply(false)
  alias(libs.plugins.kotlin.jvm).apply(false)
  alias(libs.plugins.maven.publish).apply(false)
}

dependencies {
  add("compileOnly", kotlin("gradle-plugin"))
  add("compileOnly", kotlin("gradle-plugin-api"))
  implementation(libs.dokka)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.maven.publish)
  implementation(libs.okio)

  // So the plugin can see org.gradle.accessors.dm.LibrariesForLibs
  implementation(files(libs::class.java.superclass.protectionDomain.codeSource.location))
}

gradlePlugin {
  plugins {
    create("brevityBuild") {
      id = "dev.wasmo.brevity-build"
      implementationClass = "dev.wasmo.brevity.gradle.BrevityBuildPlugin"
    }
  }
}
