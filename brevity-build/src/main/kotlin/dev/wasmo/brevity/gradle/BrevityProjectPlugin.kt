@file:OptIn(ExperimentalWasmDsl::class)

package dev.wasmo.brevity.gradle

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishBasePlugin
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

@Suppress("unused") // Used reflectively.
class BrevityBuildPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val libs = project.extensions.getByName("libs") as LibrariesForLibs

    project.extensions.add(
      BrevityBuildExtension::class.java,
      "brevityBuild",
      RealBrevityBuildExtension(
        project = project,
        libs = libs,
      ),
    )
  }
}

internal class RealBrevityBuildExtension(
  private val project: Project,
  private val libs: LibrariesForLibs,
) : BrevityBuildExtension {
  override fun library(
    jvm: Boolean,
    js: Boolean,
    wasm: Boolean,
  ) {
    project.plugins.withType<KotlinMultiplatformPluginWrapper> {
      val kotlin = project.extensions.getByName("kotlin") as KotlinMultiplatformExtension
      kotlin.apply {
        kotlin.sourceSets.commonTest {
          dependencies {
            implementation(libs.assertk)
            implementation(libs.kotlin.test)
          }
        }

        if (js) {
          js {
            browser()
          }
          kotlin.sourceSets.jsTest {
            dependencies {
              implementation(libs.kotlin.test)
              implementation(libs.kotlin.test.js)
            }
          }
        }

        if (jvm) {
          jvm()
          kotlin.sourceSets.jvmTest {
            dependencies {
              implementation(libs.kotlin.test.junit)
            }
          }
        }

        if (wasm) {
          wasmWasi {
            nodejs()
            binaries.executable()
          }
        }
      }
    }
  }

  override fun publish() {
    project.plugins.apply(libs.plugins.maven.publish.get().pluginId)
    project.plugins.apply(libs.plugins.dokka.get().pluginId)

    project.plugins.withType<MavenPublishBasePlugin> {
      project.extensions.configure<MavenPublishBaseExtension> {
        project.plugins.withType<KotlinMultiplatformPluginWrapper> {
          configure(
            KotlinMultiplatform(
              JavadocJar.Dokka("dokkaGenerateHtml"),
              SourcesJar.Sources(),
            ),
          )
        }
        project.plugins.withType<KotlinPluginWrapper> {
          configure(
            KotlinJvm(
              JavadocJar.Dokka("dokkaGenerateHtml"),
              SourcesJar.Sources(),
            ),
          )
        }
      }
    }
  }
}
