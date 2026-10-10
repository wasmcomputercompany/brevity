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
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.expressions.AbiFunction
import dev.wasmo.brevity.kotlin.expressions.AbiInterface
import dev.wasmo.brevity.kotlin.expressions.AbiLift
import dev.wasmo.brevity.kotlin.expressions.AbiResource
import dev.wasmo.brevity.kotlin.expressions.AbiService
import dev.wasmo.brevity.kotlin.expressions.AbiWorld
import dev.wasmo.brevity.kotlin.expressions.CallLifted
import dev.wasmo.brevity.kotlin.expressions.PlatformFunctionFactory
import dev.wasmo.brevity.kotlin.expressions.valueExpression

private val guestOptIns = setOf(
  Symbols.Brevity.BrevityInternalApi,
  Symbols.KotlinWasm.ComponentModelInternalApi,
  Symbols.KotlinWasm.ExperimentalWasmInterop,
  Symbols.KotlinWasm.UnsafeWasmMemoryApi,
)

class GuestGenerator(
  private val platform: GuestPlatform,
  private val platformFunctionFactory: PlatformFunctionFactory,
  private val declaredTypeEncodersGenerator: DeclaredTypeEncodersGenerator,
  private val roleTracker: RoleTracker,
  private val packages: List<IrWitPackage>,
  private val worlds: List<AbiWorld>,
  private val resources: List<AbiResource>,
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

    for (world in worlds) {
      val className = world.apiClassName
      result.collect(
        sourceSet = QualifiedSpec.SourceSet.WasmWasiMain,
        packageName = className.packageName,
        locations = setOf(world.location),
        optIns = guestOptIns,
        fileName = "${className.simpleName}Guest",
      ) {
        generateWorldEntryPoint(world)
        generateServiceClass(world)
        generateServiceFunctions(world)
        for (abiInterface in world.interfaces) {
          generateServiceClass(abiInterface)
          generateServiceFunctions(abiInterface)
        }
        retainWasmExportsFunction(world)
      }
    }
    for (abiResource in resources) {
      val className = abiResource.apiClassName
      result.collect(
        sourceSet = QualifiedSpec.SourceSet.WasmWasiMain,
        packageName = className.packageName,
        locations = setOf(abiResource.location),
        optIns = guestOptIns,
        fileName = "${className.simpleName}Guest",
      ) {
        generateServiceClass(abiResource)
        generateServiceFunctions(abiResource)
      }
    }

    return result
  }

  context(collector: QualifiedSpecCollector)
  private fun generateServiceClass(abiService: AbiService) {
    when (abiService) {
      is AbiInterface -> if (abiService.orientation != Import) return
      is AbiResource -> if (abiService.orientation != Import) return
      is AbiWorld -> {}
    }

    val className = abiService.guestServiceClassName
    val classBuilder = TypeSpec.classBuilder(className)
      .addModifiers(KModifier.INTERNAL)
      .addSuperinterface(abiService.apiInterfaceName(Import))
    val implementationConstructor = FunSpec.constructorBuilder()

    when (abiService) {
      is AbiWorld -> {
        for (abiInterface in abiService.interfaces(Import)) {
          classBuilder.addProperty(
            PropertySpec.builder(abiInterface.instanceName, abiInterface.apiClassName)
              .addModifiers(KModifier.OVERRIDE)
              .initializer("%T()", abiInterface.guestServiceClassName)
              .build(),
          )
        }
      }

      is AbiInterface -> {
      }

      is AbiResource -> {
        implementationConstructor.addParameter("id", INT)
        classBuilder.addProperty(
          PropertySpec.builder("id", INT)
            .addModifiers(KModifier.PRIVATE)
            .initializer("id")
            .build(),
        )
      }
    }

    val bridgeValue = CodeBlock.of("%T", Symbols.Brevity.GuestBridge)
    for (abiFunction in abiService.functions) {
      if (!abiFunction.isSupported) continue
      if (abiFunction.orientation != Import) continue
      classBuilder.addFunction(
        platformFunctionFactory.create(
          bridgeValue,
          abiFunction,
        ),
      )
    }

    collector.addType(
      className,
      classBuilder
        .primaryConstructor(implementationConstructor.build())
        .build(),
    )
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
            liftedFunction = CallLifted(platform, abiFunction),
          )
          val resultExpression = function.call(
            receiver = null,
            parameters = loweredParameterSpecs.map { it.valueExpression },
          )

          val returnType = abiFunction.result.loweredType?.kotlinCoreType
          if (returnType != null) {
            returns(returnType)
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
        val loweredReturnType = abiFunction.result.loweredType
        if (loweredReturnType != null) {
          returns(loweredReturnType.kotlinCoreType)
        }
      }
      .build()
  }

  context(collector: QualifiedSpecCollector)
  private fun retainWasmExportsFunction(
    world: AbiWorld,
  ) {
    collector += FunSpec.builder(world.retainWasmExportsFunctionName)
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
        callWasmExportFunctionsWithPlaceholders(world)
        for (abiInterface in world.interfaces) {
          callWasmExportFunctionsWithPlaceholders(abiInterface)
        }
        for (abiResource in resources) {
          callWasmExportFunctionsWithPlaceholders(abiResource)
        }
      }
      .build()
  }

  private fun FunSpec.Builder.callWasmExportFunctionsWithPlaceholders(service: AbiService) {
    val packageName = service.apiInterfaceName(Import).packageName
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
    is AbiWorld -> apiClassName.peerClass("Guest${apiClassName.simpleName}")
    is AbiInterface -> ClassName(
      worldServiceName.kotlinApi.packageName,
      "Guest${apiClassName.simpleName}",
    )
    is AbiResource -> type.handleName
  }
