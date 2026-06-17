plugins {
  alias(libs.plugins.kotlin.jvm)
  id("org.gradle.application")
  id("dev.wasmo.brevity-build")
}

brevityBuild {
  library(jvm = true)
  publish()
}

application {
  applicationName = "brevity"
  mainClass.set("dev.wasmo.brevity.cli.BrevityCommandKt")
}

dependencies {
  implementation(libs.clikt)
  implementation(libs.clikt.core)
  implementation(libs.kotlinpoet)
  implementation(libs.okio)
  implementation(projects.brevityKotlinGenerator)
  implementation(projects.brevityWit)
}
