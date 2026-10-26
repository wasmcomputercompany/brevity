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
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.KotlinMapper
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
  private val kotlinMapper: KotlinMapper,
  private val platformFunctionFactory: PlatformFunctionFactory,
  private val worlds: List<AbiWorld>,
) {
  fun generate(): List<QualifiedSpec> {
    val result = mutableListOf<QualifiedSpec>()

    for (abiWorld in worlds) {
      val worldClassName = abiWorld.apiClassName
      val encoderFunctionCollector = EncoderFunctionCollector(
        platform = platform,
        kotlinMapper = kotlinMapper,
        qualifiedSpecs = result,
        qualifiedSpecOptIns = guestOptIns,
        qualifiedSpecParent = QualifiedSpec.Parent.File(
          sourceSet = QualifiedSpec.SourceSet.WasmWasiMain,
          packageName = worldClassName.packageName,
          fileName = "${worldClassName.simpleName}GuestEncoders",
        ),
        world = abiWorld,
        typeNamer = GuestTypeNamer,
      )

      for (abiService in abiWorld.allServices) {
        val className = abiService.apiClassName
        context(
          result.collector(
            sourceSet = QualifiedSpec.SourceSet.WasmWasiMain,
            locations = setOf(abiService.location),
            optIns = guestOptIns,
            packageName = className.packageName,
            fileName = "${className.simpleName}Guest",
          ),
        ) {
          generateServiceFunctions(encoderFunctionCollector, abiService)
          when (abiService) {
            is AbiResource -> generateAbiResourceClass(encoderFunctionCollector, abiService)
            is AbiInterface -> generateAbiInterfaceClass(encoderFunctionCollector, abiService)
            is AbiWorld -> {
              generateWorldEntryPoint(abiWorld)
              generateAbiWorldClass(encoderFunctionCollector, abiWorld)
              retainWasmExportsFunction(abiWorld)
            }
          }
        }
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
          .apply {
            addStatement("%M()", Symbols.Brevity.RetainWasmExportsForGuestBridge)
            addStatement("%N()", abiWorld.retainWasmExportsFunctionName)
            addStatement("%N = %N", "${guestApisInstanceName}_", "value")
          }
          .build(),
      )
      .build()

    val hostApisInstanceName = "host"
    collector += PropertySpec.builder(hostApisInstanceName, abiWorld.apiInterfaceName(Import))
      .receiver(abiWorld.apiClassName)
      .getter(
        FunSpec.getterBuilder()
          .addCode("return %T", GuestTypeNamer.implementation(abiWorld))
          .build(),
      )
      .build()
  }

  context(collector: QualifiedSpecCollector)
  private fun generateAbiWorldClass(
    encoderFunctionCollector: EncoderFunctionCollector,
    abiWorld: AbiWorld,
  ) {
    val className = GuestTypeNamer.implementation(abiWorld)
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
                .initializer("%T()", GuestTypeNamer.implementation(abiInterface))
                .build(),
            )
          }
        }
        .declareServiceClassFunctions(encoderFunctionCollector, abiWorld)
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateAbiInterfaceClass(
    encoderFunctionCollector: EncoderFunctionCollector,
    abiInterface: AbiInterface,
  ) {
    if (abiInterface.orientation != Import) return
    val className = GuestTypeNamer.implementation(abiInterface)
    collector.addType(
      className,
      TypeSpec.classBuilder(className)
        .addModifiers(KModifier.INTERNAL)
        .addSuperinterface(abiInterface.apiInterfaceName(Import))
        .declareServiceClassFunctions(encoderFunctionCollector, abiInterface)
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateAbiResourceClass(
    encoderFunctionCollector: EncoderFunctionCollector,
    abiResource: AbiResource,
  ) {
    if (abiResource.orientation != Import) return
    val className = GuestTypeNamer.implementation(abiResource)
    collector.addType(
      className,
      TypeSpec.classBuilder(className)
        .addModifiers(KModifier.INTERNAL)
        .superclass(abiResource.apiInterfaceName(Import))
        .declareServiceClassFunctions(encoderFunctionCollector, abiResource)
        .build(),
    )
  }

  private fun TypeSpec.Builder.declareServiceClassFunctions(
    encoderFunctionCollector: EncoderFunctionCollector,
    abiService: AbiService,
  ) = apply {
    val bridgeValue = CodeBlock.of("%T", Symbols.Brevity.GuestBridge)
    for (abiFunction in abiService.functions) {
      if (!abiFunction.isSupported) continue
      if (abiFunction.orientation != Import) continue
      if (abiFunction.isSynthetic) continue
      addFunction(
        platformFunctionFactory.create(
          encoderFunctionCollector = encoderFunctionCollector,
          bridge = bridgeValue,
          abiService = abiService,
          abiFunction = abiFunction,
        ),
      )
    }
  }

  context(collector: QualifiedSpecCollector)
  private fun generateServiceFunctions(
    encoderFunctionCollector: EncoderFunctionCollector,
    abiService: AbiService,
  ) {
    for (abiFunction in abiService.functions) {
      if (!abiFunction.isSupported) continue
      collector += when (abiFunction.orientation) {
        Export -> wasmExport(encoderFunctionCollector, abiService, abiFunction)
        Import -> wasmImport(abiFunction)
      }
    }
  }

  /** Returns the `@WasmExport`-annotated function. It must be added directly to a file. */
  private fun wasmExport(
    encoderFunctionCollector: EncoderFunctionCollector,
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
            functionEncoder = FunctionEncoder(
              collector = encoderFunctionCollector,
              service = parentService,
              function = abiFunction,
            ),
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
      .addModifiers(KModifier.INTERNAL, KModifier.EXTERNAL)
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
  private fun retainWasmExportsFunction(abiWorld: AbiWorld) {
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
        for (abiService in abiWorld.allServices) {
          val packageName = abiService.apiClassName.packageName
          for (abiFunction in abiService.functions) {
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
      }
      .build()
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

private val AbiWorld.allServices: List<AbiService>
  get() = buildList {
    add(this@allServices)
    for (abiInterface in interfaces) {
      add(abiInterface)
      addAll(abiInterface.resources)
    }
    addAll(resources)
  }

private val AbiWorld.retainWasmExportsFunctionName: String
  get() = "retainWasmExportsFor${apiClassName.simpleName}"

private val guestOptIns = setOf(
  Symbols.Brevity.BrevityInternalApi,
  Symbols.KotlinWasm.ComponentModelInternalApi,
  Symbols.KotlinWasm.ExperimentalWasmInterop,
  Symbols.KotlinWasm.UnsafeWasmMemoryApi,
)

/** Guests implementations are named using a top-level class. */
private object GuestTypeNamer : TypeNamer() {
  override fun implementation(abiWorld: AbiWorld): ClassName {
    val apiClassName = abiWorld.apiClassName
    return apiClassName.peerClass("${apiClassName.simpleName}Bridge")
  }

  override fun implementation(abiInterface: AbiInterface): ClassName {
    val apiClassName = abiInterface.apiClassName
    return apiClassName.peerClass("Guest${apiClassName.simpleName}")
  }

  override fun implementation(type: TypeName.Declared): ClassName {
    return ClassName(
      type.serviceName.packageName.kotlinApi,
      "${type.serviceName.name.upperCamelCase}${type.name.upperCamelCase}Handle",
    )
  }
}
