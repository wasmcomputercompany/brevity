package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.generator.GuestFunctionFactory.Receiver

private val guestOptIns = setOf(
  Symbols.KotlinWasm.ComponentModelInternalApi,
  Symbols.KotlinWasm.ExperimentalWasmInterop,
  Symbols.KotlinWasm.UnsafeWasmMemoryApi,
)

class GuestGenerator(
  private val encoderFactory: EncoderFactory,
  private val declarationIndex: DeclarationIndex,
  private val declaredTypeEncodersGenerator: DeclaredTypeEncodersGenerator,
  private val roleTracker: RoleTracker,
  private val packages: List<IrWitPackage>,
) {
  fun generate(): List<QualifiedSpec> {
    val result = mutableListOf<QualifiedSpec>()

    for (service in packages.flatMap { it.services }) {
      for (type in service.types) {
        val typeName = type.type
        val className = typeName.kotlinApi
        // TODO: this is hacked because we don't also prune unreachable callsites.
        val roles = (RoleTracker.Entry(true, true) ?: roleTracker[typeName])!!
        val fileName = className.simpleNames.joinToString(separator = "") + "Guest"

        result.collect(
          sourceSet = QualifiedSpec.SourceSet.WasmWasiMain,
          locations = setOf(type.location),
          optIns = guestOptIns,
          packageName = className.packageName,
          fileName = fileName,
        ) {
          generateTypeFunctions(type, roles)
          declaredTypeEncodersGenerator.generate(type, roles)
        }
      }

      val className = service.serviceName.kotlinApi
      result.collect(
        packageName = className.packageName,
        locations = setOf(service.location),
        optIns = guestOptIns,
        fileName = "${className.simpleName}Guest",
        sourceSet = QualifiedSpec.SourceSet.WasmWasiMain,
      ) {
        generateService(service)
        if (service is IrWorld) {
          retainWasmExportsFunction(service)
          addExternalFunctions(service)
        }
      }
    }

    return result
  }

  context(collector: QualifiedSpecCollector)
  private fun generateService(value: IrWitPackage.Service) {
    if (value is IrWorld) {
      val guestApis = value.guestApis
      if (guestApis != null) {
        collector += PropertySpec.builder("${guestApis.instanceName}_", guestApis.type)
          .addModifiers(KModifier.PRIVATE, KModifier.LATEINIT)
          .mutable(true)
          .build()
        collector += PropertySpec.builder(guestApis.instanceName, guestApis.type)
          .receiver(value.serviceName.kotlinApi)
          .mutable(true)
          .getter(
            FunSpec.getterBuilder()
              .addCode("return %N", "${guestApis.instanceName}_")
              .build(),
          )
          .setter(
            FunSpec.setterBuilder()
              .addParameter("value", guestApis.type)
              .addStatement("%M()", Symbols.Brevity.RetainWasmExportsForGuestBridge)
              .addStatement("%N()", value.retainWasmExportsFunctionName)
              .addCode("%N = %N", "${guestApis.instanceName}_", "value")
              .build(),
          )
          .build()
      }
    }
  }

  context(collector: QualifiedSpecCollector)
  private fun generateTypeFunctions(
    typeDeclaration: IrTypeDeclaration,
    entry: RoleTracker.Entry,
  ) {
    if (typeDeclaration is IrResource) {
      generateResourceFunctions(
        value = typeDeclaration,
        host = entry.host,
        guest = entry.guest,
      )
    }
  }

  context(collector: QualifiedSpecCollector)
  private fun generateResourceFunctions(
    value: IrResource,
    host: Boolean,
    guest: Boolean,
  ) {
    val receiver = Receiver.Id(
      type = value.type,
    )

    if (guest) {
      for (function in value.functions) {
        if (!function.isSupported) continue // TODO
        collector += GuestFunctionFactory(encoderFactory, receiver, function).wasmExport()
      }
    }

    if (host) {
      val handleBuilder = TypeSpec.classBuilder(value.type.handleName)
        .addModifiers(KModifier.INTERNAL)
        .addSuperinterface(value.type.kotlinApi)
        .primaryConstructor(
          FunSpec.constructorBuilder()
            .addParameter("id", INT)
            .build(),
        )
        .addProperty(
          PropertySpec.builder("id", INT)
            .addModifiers(KModifier.PRIVATE)
            .initializer("id")
            .build(),
        )

      for (function in value.functions) {
        if (!function.isSupported) continue // TODO
        handleBuilder.addFunction(
          GuestFunctionFactory(encoderFactory, receiver, function).callHost(),
        )
        collector += GuestFunctionFactory(encoderFactory, receiver, function).wasmImport()
      }

      collector.addType(value.type.handleName, handleBuilder.build())
    }
  }

  /**
   * Generate top-level `@WasmExport`-annotated functions for all exported functions in [value],
   * and functions recursively held by [value].
   */
  context(collector: QualifiedSpecCollector)
  private fun addExternalFunctions(value: IrWorld) {
    for (factory in exportedGuestFunctionFactories(value)) {
      collector += factory.wasmExport()
    }
  }

  private fun exportedGuestFunctionFactories(value: IrWorld): List<GuestFunctionFactory> {
    return buildList {
      // The object to dereference that defines the true implementation. This is either the guest
      // interface or one of its members.
      val guestApis = value.guestApis ?: return@buildList

      for (item in guestApis.items) {
        when (item) {
          is IrFunction -> {
            val receiver = Receiver.Global(
              CodeBlock.of("%N_", guestApis.instanceName),
            )
            add(GuestFunctionFactory(encoderFactory, receiver, item))
          }

          is IrExternalApi -> {
            val irInterface = declarationIndex[item.serviceName] as IrInterface
            val receiver = Receiver.Global(
              CodeBlock.of("%N_.%N", guestApis.instanceName, item.instanceName),
            )
            for (function in irInterface.functions) {
              add(GuestFunctionFactory(encoderFactory, receiver, function))
            }
          }
        }
      }
    }
  }

  context(collector: QualifiedSpecCollector)
  private fun retainWasmExportsFunction(value: IrWorld) {
    collector += FunSpec.builder(value.retainWasmExportsFunctionName)
      .addModifiers(KModifier.PRIVATE)
      .addKdoc(
        """
        |This function does nothing. But by calling it the compiler retains exported symbols that
        |would otherwise be eliminated as unused.
        |
        |https://youtrack.jetbrains.com/issue/KT-88068/
        """.trimMargin(),
      )
      .addStatement("// Equivalent to 'if (true) return', but immune to dead code elimination.")
      .addStatement("if (%S.hashCode() == 0) return", "")
      .apply {
        for (factory in exportedGuestFunctionFactories(value)) {
          addStatement("%L", factory.callWasmExportFunctionWithPlaceholders())
        }
      }
      .build()
  }
}
