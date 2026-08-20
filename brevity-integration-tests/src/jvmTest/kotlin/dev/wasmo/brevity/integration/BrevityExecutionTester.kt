package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.kotlin.generator.WitBridgeGenerator
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase
import dev.wasmo.brevity.kotlin.generator.upperCamelCase
import dev.wasmo.brevity.withIssueCollector
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.source

/**
 * Generate a bunch of code:
 *  - `.wit`
 *  - `.kt` via Brevity
 *  - `.kt` manually with string templating
 *
 * Then compile and execute the generated code to confirm that types are bridged correctly.
 *
 * This uses the Kotlin Toolchain.
 * https://kotlin-toolchain.org/latest/
 *
 * This requires Brevity artifacts are published to Maven Local.
 *
 * ```
 * ./gradlew publishAllPublicationsToMavenLocalRepository
 * ```
 */
class BrevityExecutionTester(
  val fileSystem: FileSystem = FileSystem.SYSTEM,
  val path: Path,
  val types: List<SampleType>,
) {
  private val witPath = path / "wit"
  private val apiPath = path / "api"
  private val apiSrcPath = apiPath / "src"
  private val guestAppPath = path / "guest"
  private val guestAppSrcPath = guestAppPath / "src"
  private val hostAppPath = path / "host"
  private val hostAppSrcPath = hostAppPath / "src"

  fun execute() {
    fileSystem.deleteRecursively(path)

    generateWit()
    generateBrevity()
    generateKotlinProject()
    generateApiKotlinModule()
    generateGuestAppKotlinModule()
    generateHostAppKotlinModule()
    generateKotlinGuest()
    generateKotlinHost()
    compileKotlinGuest()
    compileKotlinHost()
    executeKotlinHostKotlinGuest()
  }

  fun generateWit() {
    fileSystem.createDirectories(witPath)
    fileSystem.write(witPath / "bridge-type-test.wit") {
      writeUtf8(
        """
        |package wasmo:testing;
        |
        |world bridge-type-test {
        |
        """.trimMargin(),
      )
      for (type in types) {
        writeUtf8(
          """
          |  export pass-as-parameter-${type.idLower}: func(v: ${type.witType});
          |
          """.trimMargin(),
        )
      }
      writeUtf8(
        """
        |}
        |
        """.trimMargin(),
      )
    }
  }

  fun generateBrevity() {
    withIssueCollector {
      val generator = WitBridgeGenerator.precompile(
        fileSystem = fileSystem,
        packageDirectories = listOf(witPath),
      ) ?: return@withIssueCollector

      for (fileSpec in generator.api.generate()) {
        fileSpec.writeTo(apiSrcPath.toFile())
      }
      for (fileSpec in generator.guest.generate()) {
        fileSpec.writeTo(guestAppSrcPath.toFile())
      }
      for (fileSpec in generator.host.generate()) {
        fileSpec.writeTo(hostAppSrcPath.toFile())
      }
    }
  }

  fun generateKotlinProject() {
    fileSystem.createDirectories(path)
    fileSystem.write(path / "project.yaml") {
      writeUtf8(
        """
        |modules:
        |  - ./api
        |  - ./guest
        |  - ./host
        |
        """.trimMargin(),
      )
    }
  }

  fun generateApiKotlinModule() {
    fileSystem.createDirectories(apiPath)
    fileSystem.write(apiPath / "module.yaml") {
      writeUtf8(
        """
        |product:
        |  type: kmp/lib
        |  platforms: [jvm, wasmWasi]
        |
        |settings:
        |  kotlin:
        |    version: 2.4.0
        |  jvm:
        |    jdk:
        |      version: 25
        |
        |repositories:
        |  - mavenLocal
        |
        |dependencies:
        |  - com.squareup.okio:okio:3.16.4
        |  - dev.wasmo.brevity:brevity:0.1.0
        |  - dev.wasmo.brevity:brevity-wasi-p1:0.1.0
        |  - dev.wasmo.brevity:brevity-wasi-p2:0.1.0
        |
        |dependencies@jvm:
        |  - com.dylibso.chicory:runtime:1.7.5
        |
        """.trimMargin(),
      )
    }
  }

  fun generateGuestAppKotlinModule() {
    fileSystem.createDirectories(guestAppPath)
    fileSystem.write(guestAppPath / "module.yaml") {
      writeUtf8(
        """
        |product:
        |  type: wasm-wasi/app
        |
        |settings:
        |  kotlin:
        |    version: 2.4.0
        |  jvm:
        |    jdk:
        |      version: 25
        |
        |repositories:
        |  - mavenLocal
        |
        |dependencies:
        |  - ../api
        |  - com.squareup.okio:okio:3.16.4
        |  - dev.wasmo.brevity:brevity:0.1.0
        |
        """.trimMargin(),
      )
    }
  }

  fun generateHostAppKotlinModule() {
    fileSystem.createDirectories(hostAppPath)
    fileSystem.write(hostAppPath / "module.yaml") {
      writeUtf8(
        """
        |product:
        |  type: jvm/app
        |
        |settings:
        |  kotlin:
        |    version: 2.4.0
        |  jvm:
        |    jdk:
        |      version: 25
        |    mainClass: dev.wasmo.brevity.integration.HostMainKt
        |
        |repositories:
        |  - mavenLocal
        |
        |dependencies:
        |  - ../api
        |  - com.squareup.okio:okio:3.16.4
        |  - dev.wasmo.brevity:brevity:0.1.0
        |  - dev.wasmo.brevity:brevity-wasi-p1:0.1.0
        |  - dev.wasmo.brevity:brevity-wasi-p2:0.1.0
        |  - com.dylibso.chicory:runtime:1.7.5
        |
        """.trimMargin(),
      )
    }
  }

  fun generateKotlinGuest() {
    val guestImplementationPath =
      guestAppSrcPath / "dev/wasmo/brevity/integration/GuestImplementation.kt"
    fileSystem.createDirectories(guestImplementationPath.parent!!)
    fileSystem.write(guestImplementationPath) {
      writeUtf8(
        """
        |@file:OptIn(
        |  ComponentModelInternalApi::class,
        |  ExperimentalStdlibApi::class,
        |  ExperimentalWasmInterop::class,
        |  UnsafeWasmMemoryApi::class,
        |)
        |package dev.wasmo.brevity.integration
        |
        |import kotlin.wasm.unsafe.ComponentModelInternalApi
        |import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
        |import kotlin.wasm.unsafe.componentModelRealloc
        |import wit.wasmo.testing.BridgeTypeTest
        |import wit.wasmo.testing.guest
        |
        |@WasmExport(name = "cabi_realloc")
        |fun cabi_realloc(ptr: Int, oldSize: Int, align: Int, newSize: Int): Int =
        |  componentModelRealloc(ptr, oldSize, newSize)
        |
        |@EagerInitialization
        |val actuallyInitialize = run {
        |  BridgeTypeTest.guest = GuestImplementation
        |}
        |
        |
        """.trimMargin(),
      )
      writeUtf8(
        """
        |object GuestImplementation : BridgeTypeTest.Guest {
        |
        """.trimMargin(),
      )
      for (type in types) {
        writeUtf8(
          """
          |  override fun passAsParameter${type.idUpper}(v: ${type.kotlinType}) {
          |    val index = when (v) {
          |
          """.trimMargin(),
        )
        for ((index, value) in type.values.withIndex()) {
          writeUtf8(
            """
            |      ${value.kotlin} -> $index
            |
            """.trimMargin(),
          )
        }
        writeUtf8(
          """
          |      else -> -1
          |    }
          |    println("${type.idLower} index=${'$'}index")
          |  }
          |
          """.trimMargin(),
        )
      }
      writeUtf8(
        """
        |}
        |
        """.trimMargin(),
      )
    }
  }

  fun generateKotlinHost() {
    val hostMainPath = hostAppSrcPath / "dev/wasmo/brevity/integration/HostMain.kt"
    fileSystem.createDirectories(hostMainPath.parent!!)
    fileSystem.write(hostMainPath) {
      writeUtf8(
        """
        |package dev.wasmo.brevity.integration
        |
        |import dev.wasmo.brevity.WasmInstance
        |import okio.Path.Companion.toPath
        |import wit.wasi.v0_1.SystemWasiP1
        |import wit.wasi.v0_1.Wasi
        |import wit.wasi.v0_1.World
        |import wit.wasmo.testing.BridgeTypeTest
        |import wit.wasmo.testing.World
        |
        |fun main(vararg args: String) {
        |  val world = BridgeTypeTest.World { }
        |  WasmInstance(
        |    path = args[0].toPath(),
        |    worlds = listOf(
        |      Wasi.World({ SystemWasiP1 }),
        |      world,
        |    ),
        |  )
        |
        |
        """.trimMargin(),
      )
      for (type in types) {
        for ((index, value) in type.values.withIndex()) {
          writeUtf8(
            """
            |  println("${type.idLower} index=$index")
            |  world.guest.passAsParameter${type.idUpper}(${value.kotlin})
            |
            """.trimMargin(),
          )
        }
      }
      writeUtf8(
        """
        |}
        |
        """.trimMargin(),
      )
    }
  }

  fun compileKotlinGuest() {
    val process = ProcessBuilder()
      .directory(path.toFile())
      .command(
        "kotlin",
        "build",
        "--module",
        "guest",
      )
      .redirectError(ProcessBuilder.Redirect.INHERIT)
      .start()

    val output = Buffer()
    output.writeAll(process.inputStream.source())

    println(output.readUtf8())
  }

  fun compileKotlinHost() {
    val process = ProcessBuilder()
      .directory(path.toFile())
      .command(
        "kotlin",
        "package",
        "--module",
        "host",
      )
      .redirectError(ProcessBuilder.Redirect.INHERIT)
      .start()

    val output = Buffer()
    output.writeAll(process.inputStream.source())

    println(output.readUtf8())
  }

  fun executeKotlinHostKotlinGuest() {
    val process = ProcessBuilder()
      .directory(path.toFile())
      .command(
        "java",
        "-jar",
        "build/tasks/_host_executableJarJvm/host-jvm-executable.jar",
        "build/tasks/_guest_linkWasmWasi/guest.wasm",
      )
      .redirectError(ProcessBuilder.Redirect.INHERIT)
      .start()

    val output = Buffer()
    output.writeAll(process.inputStream.source())

    println(output.readUtf8())
  }
}

data class SampleType(
  val id: Identifier,
  val witType: String,
  val kotlinType: String,
  val values: List<SampleValue>,
) {
  val idUpper: String
    get() = id.upperCamelCase
  val idLower: String
    get() = id.lowerCamelCase
}

data class SampleValue(
  val kotlin: String,
  val rust: String,
)
