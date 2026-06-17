import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

plugins {
  `kotlin-dsl`
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
    create("brevity-build") {
      id = "brevity-build"
      implementationClass = "dev.wasmo.brevity.gradle.BrevityBuildPlugin"
    }
  }
}

allprojects {
  plugins.withType<KotlinMultiplatformPluginWrapper> {
    extensions.configure<KotlinMultiplatformExtension> {
      compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
      }
    }
  }

  plugins.withType<KotlinPluginWrapper> {
    extensions.configure<KotlinJvmExtension> {
      compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
      }
    }
  }
}
