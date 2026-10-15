package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.Orientation.Import
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.kotlin.abi.AbiFunction
import dev.wasmo.brevity.kotlin.abi.AbiInterface
import dev.wasmo.brevity.kotlin.abi.AbiLift
import dev.wasmo.brevity.kotlin.abi.AbiResource
import dev.wasmo.brevity.kotlin.abi.AbiService
import dev.wasmo.brevity.kotlin.abi.AbiWorld
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.expressions.valueExpression

class GuestGenerator(
  private val platform: GuestPlatform,
  private val platformFunctionFactory: PlatformFunctionFactory,
  private val declaredTypeEncodersGenerator: DeclaredTypeEncodersGenerator,
  private val roleTracker: RoleTracker,
  private val packages: List<IrWitPackage>,
  private val worlds: List<AbiWorld>,
) {
  fun generate(): List<QualifiedSpec> {
    val result = mutableListOf<QualifiedSpec>()

    for (type in packages.flatMap { it.services }.flatMap { it.types }) {
      val typeName = type.type
      // TODO: this is hacked because we don't also prune unreachable callsites.
      val roles = (RoleTracker.Entry(true, true) ?: roleTracker[typeName])!!

      result.collect(
        sourceSet = QualifiedSpec.SourceSet.WasmWasiMain,
        locations = setOf(type.location),
        optIns = guestOptIns,
        packageName = typeName.serviceName.kotlinApi.packageName,
        fileName = "${typeName.name.upperCamelCase}Guest",
      ) {
        declaredTypeEncodersGenerator.generate(type, roles)
      }
    }

    for (abiWorld in worlds) {
      val className = abiWorld.apiClassName
      result.collect(
        sourceSet = QualifiedSpec.SourceSet.WasmWasiMain,
        packageName = className.packageName,
        locations = setOf(abiWorld.location),
        optIns = guestOptIns,
        fileName = "${className.simpleName}Guest",
      ) {
        generateWorldEntryPoint(abiWorld)
        generateAbiWorldClass(abiWorld)
        for (abiInterface in abiWorld.interfaces) {
          generateAbiInterfaceClass(abiInterface)
          for (abiResource in abiInterface.resources) {
            generateAbiResourceClass(abiResource)
          }
        }
        for (abiResource in abiWorld.resources) {
          generateAbiResourceClass(abiResource)
        }
        retainWasmExportsFunction(abiWorld)
      }
    }

    return result
  }

  context(collector: QualifiedSpecCollector)
  private fun generateWorldEntryPoint(abiWorld: AbiWorld) {
    val guestApisType = abiWorld.apiInterfaceName(Export)
    val guestApisInstanceName = "guest"
    collector += PropertySpec.builder("${guestApisInstanceName}_", guestApisType)
      .addModifiers(KModifier.INTERNAL, KModifier.LATEINIT)
      .mutable(true)
      .build()
    collector += PropertySpec.builder(guestApisInstanceName, guestApisType)
      .receiver(abiWorld.apiClassName)
      .mutable(true)
      .getter(
        FunSpec.getterBuilder()
          .addCode("return %N", "${guestApisInstanceName}_")
          .build(),
      )
      .setter(
        FunSpec.setterBuilder()
          .addParameter("value", guestApisType)
          .addStatement("%M()", Symbols.Brevity.RetainWasmExportsForGuestBridge)
          .addStatement("%N()", abiWorld.retainWasmExportsFunctionName)
          .addCode("%N = %N", "${guestApisInstanceName}_", "value")
          .build(),
      )
      .build()

    val hostApisInstanceName = "host"
    collector += PropertySpec.builder(hostApisInstanceName, abiWorld.apiInterfaceName(Import))
      .receiver(abiWorld.apiClassName)
      .getter(
        FunSpec.getterBuilder()
          .addCode("return %T", abiWorld.guestServiceClassName)
          .build(),
      )
      .build()
  }

  context(collector: QualifiedSpecCollector)
  private fun generateAbiWorldClass(abiWorld: AbiWorld) {
    val className = abiWorld.guestServiceClassName
    collector.addType(
      className,
      TypeSpec.objectBuilder(className)
        .addModifiers(KModifier.INTERNAL)
        .addSuperinterface(abiWorld.apiInterfaceName(Import))
        .apply {
          for (abiInterface in abiWorld.interfaces(Import)) {
            addProperty(
              PropertySpec.builder(abiInterface.instanceName, abiInterface.apiClassName)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("%T()", abiInterface.guestServiceClassName)
                .build(),
            )
          }
        }
        .declareServiceClassFunctions(abiWorld)
        .build(),
    )
    generateServiceFunctions(abiWorld)
  }

  context(collector: QualifiedSpecCollector)
  private fun generateAbiInterfaceClass(abiInterface: AbiInterface) {
    if (abiInterface.orientation == Import) {
      val className = abiInterface.guestServiceClassName
      collector.addType(
        className,
        TypeSpec.classBuilder(className)
          .addModifiers(KModifier.INTERNAL)
          .addSuperinterface(abiInterface.apiInterfaceName(Import))
          .declareServiceClassFunctions(abiInterface)
          .build(),
      )
    }
    generateServiceFunctions(abiInterface)
  }

  context(collector: QualifiedSpecCollector)
  private fun generateAbiResourceClass(abiResource: AbiResource) {
    if (abiResource.orientation == Import) {
      val className = abiResource.guestServiceClassName
      collector.addType(
        className,
        TypeSpec.classBuilder(className)
          .addModifiers(KModifier.INTERNAL)
          .addSuperinterface(abiResource.apiInterfaceName(Import))
          .addProperty(
            PropertySpec.builder("id", INT)
              .addModifiers(KModifier.PRIVATE)
              .initializer("id")
              .build(),
          )
          .primaryConstructor(
            FunSpec.constructorBuilder()
              .addParameter("id", INT)
              .build(),
          )
          .declareServiceClassFunctions(abiResource)
          .build(),
      )
    }
    generateServiceFunctions(abiResource)
  }

  private fun TypeSpec.Builder.declareServiceClassFunctions(abiService: AbiService) = apply {
    val bridgeValue = CodeBlock.of("%T", Symbols.Brevity.GuestBridge)
    for (abiFunction in abiService.functions) {
      if (!abiFunction.isSupported) continue
      if (abiFunction.orientation != Import) continue
      if (abiFunction.isSynthetic) continue
      addFunction(
        platformFunctionFactory.create(
          bridgeValue,
          abiFunction,
        ),
      )
    }
  }

  context(collector: QualifiedSpecCollector)
  private fun generateServiceFunctions(abiService: AbiService) {
    for (abiFunction in abiService.functions) {
      if (!abiFunction.isSupported) continue
      collector += when (abiFunction.orientation) {
        Export -> wasmExport(abiService, abiFunction)
        Import -> wasmImport(abiFunction)
      }
    }
  }

  /** Returns the `@WasmExport`-annotated function. It must be added directly to a file. */
  private fun wasmExport(
    parentService: AbiService,
    abiFunction: AbiFunction,
  ): FunSpec {
    val codeBuilder = CodeBuilder(
      bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
      platform = platform,
      nameAllocator = abiFunction.newNameAllocator(),
    )

    val loweredParameterSpecs = abiFunction.loweredParameterSpecs

    return FunSpec.builder(abiFunction.name.exportFunctionName)
      .addAnnotation(abiFunction.name.wasmExportAnnotation)
      .addModifiers(KModifier.INTERNAL)
      .addParameters(loweredParameterSpecs)
      .apply {
        context(codeBuilder) {
          val function = AbiLift(
            parentService = parentService,
            abiFunction = abiFunction,
          )
          val resultExpression = function.call(
            receiver = null,
            parameters = loweredParameterSpecs.map { it.valueExpression },
          )

          val loweredReturnType = abiFunction.loweredReturnType
          if (loweredReturnType != null) {
            returns(loweredReturnType.kotlinCoreType)
            codeBuilder.addStatement("return %L", resultExpression.code)
          }
        }
      }
      .addCode(codeBuilder.build())
      .build()
  }

  /** Returns the `@WasmImport`-annotated function. It must be added directly to a file. */
  private fun wasmImport(abiFunction: AbiFunction): FunSpec {
    return FunSpec.builder(abiFunction.name.importFunctionName)
      .addAnnotation(abiFunction.name.wasmImportAnnotation)
      .addModifiers(KModifier.PRIVATE, KModifier.EXTERNAL)
      .apply {
        addParameters(abiFunction.loweredParameterSpecs)
        val loweredReturnType = abiFunction.loweredReturnType
        if (loweredReturnType != null) {
          returns(loweredReturnType.kotlinCoreType)
        }
      }
      .build()
  }

  context(collector: QualifiedSpecCollector)
  private fun retainWasmExportsFunction(
    abiWorld: AbiWorld,
  ) {
    collector += FunSpec.builder(abiWorld.retainWasmExportsFunctionName)
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
        val packageName = abiWorld.apiClassName.packageName
        callWasmExportFunctionsWithPlaceholders(
          packageName = packageName,
          service = abiWorld,
        )
        for (abiInterface in abiWorld.interfaces) {
          callWasmExportFunctionsWithPlaceholders(
            packageName = packageName,
            service = abiInterface,
          )
          for (abiResource in abiInterface.resources) {
            callWasmExportFunctionsWithPlaceholders(
              packageName = abiResource.apiClassName.packageName,
              service = abiResource,
            )
          }
        }
        for (abiResource in abiWorld.resources) {
          callWasmExportFunctionsWithPlaceholders(
            packageName = abiResource.apiClassName.packageName,
            service = abiResource,
          )
        }
      }
      .build()
  }

  private fun FunSpec.Builder.callWasmExportFunctionsWithPlaceholders(
    packageName: String,
    service: AbiService,
  ) {
    for (abiFunction in service.functions) {
      if (!abiFunction.isSupported) continue
      if (abiFunction.orientation != Export) continue
      val memberName = MemberName(packageName, abiFunction.name.exportFunctionName)
      addCode("%M(", memberName)
      for ((index, spec) in abiFunction.loweredParameterSpecs.withIndex()) {
        if (index > 0) addCode(", ")
        addCode(spec.placeholder)
      }
      addCode(")\n")
    }
  }

  private val ParameterSpec.placeholder: CodeBlock
    get() = when (type) {
      INT -> CodeBlock.of("%L", 0)
      LONG -> CodeBlock.of("%LL", 0)
      FLOAT -> CodeBlock.of("%Lf", 0.0)
      DOUBLE -> CodeBlock.of("%L", 0.0)
      else -> error("unexpected core parameter type")
    }
}

val AbiWorld.retainWasmExportsFunctionName: String
  get() = "retainWasmExportsFor${apiClassName.simpleName}"

val AbiService.guestServiceClassName: ClassName
  get() = when (this) {
    is AbiWorld -> apiClassName.peerClass("${apiClassName.simpleName}Bridge")
    is AbiInterface -> ClassName(
      worldServiceName.kotlinApi.packageName,
      "Guest${apiClassName.simpleName}",
    )

    is AbiResource -> type.handleName
  }

private val guestOptIns = setOf(
  Symbols.Brevity.BrevityInternalApi,
  Symbols.KotlinWasm.ComponentModelInternalApi,
  Symbols.KotlinWasm.ExperimentalWasmInterop,
  Symbols.KotlinWasm.UnsafeWasmMemoryApi,
)
