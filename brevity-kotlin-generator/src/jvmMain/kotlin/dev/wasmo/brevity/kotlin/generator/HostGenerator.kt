package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG_ARRAY
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.Orientation.Import
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.valType
import dev.wasmo.brevity.kotlin.expressions.AbiFunction
import dev.wasmo.brevity.kotlin.expressions.AbiInterface
import dev.wasmo.brevity.kotlin.expressions.AbiLift
import dev.wasmo.brevity.kotlin.expressions.AbiResource
import dev.wasmo.brevity.kotlin.expressions.AbiService
import dev.wasmo.brevity.kotlin.expressions.AbiWorld
import dev.wasmo.brevity.kotlin.expressions.CallLifted
import dev.wasmo.brevity.kotlin.expressions.ChicoryImportFunction
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.PlatformFunctionFactory

private val hostOptIns = setOf(
  Symbols.Brevity.BrevityInternalApi,
)

class HostGenerator(
  private val platformFunctionFactory: PlatformFunctionFactory,
  private val hostPlatform: HostPlatform,
  private val declaredTypeEncodersGenerator: DeclaredTypeEncodersGenerator,
  private val roleTracker: RoleTracker,
  private val packages: List<IrWitPackage>,
  private val worlds: List<AbiWorld>,
  private val resources: List<AbiResource>,
  private val guestResourcesSupported: Boolean = false,
) {
  fun generate(): List<QualifiedSpec> {
    val result = mutableListOf<QualifiedSpec>()

    for (type in packages.flatMap { it.services }.flatMap { it.types }) {
      val typeName = type.type
      // TODO: this is hacked because we don't also prune unreachable callsites.
      val roles = (RoleTracker.Entry(true, true) ?: roleTracker[typeName])!!

      result.collect(
        sourceSet = QualifiedSpec.SourceSet.JvmMain,
        locations = setOf(type.location),
        optIns = hostOptIns,
        packageName = typeName.serviceName.kotlinApi.packageName,
        fileName = "${typeName.name.upperCamelCase}Host",
      ) {
        declaredTypeEncodersGenerator.generate(type, roles)
      }
    }

    for (abiWorld in worlds) {
      val className = abiWorld.apiClassName
      result.collect(
        sourceSet = QualifiedSpec.SourceSet.JvmMain,
        packageName = className.packageName,
        locations = setOf(abiWorld.location),
        optIns = hostOptIns,
        fileName = "${className.simpleName}Host",
      ) {
        generateWorldFactoryFunction(abiWorld)
        generateWorld(abiWorld)
      }
    }

    return result
  }

  context(collector: QualifiedSpecCollector)
  private fun generateWorldFactoryFunction(value: AbiWorld) {
    // The implemented World interface uses the interface types; everything else uses the
    // implementation types.
    val guestApisType = value.apiInterfaceName(Export)
    val hostApisType = value.apiInterfaceName(Import)
    val worldType = Symbols.Brevity.World.parameterizedBy(hostApisType, guestApisType)

    val hostFactory = ParameterSpec.builder(
      "hostFactory",
      LambdaTypeName.get(
        parameters = listOf(ParameterSpec.unnamed(guestApisType)),
        returnType = hostApisType,
      ),
    ).defaultValue(defaultHostFactory(value))
      .build()

    collector += FunSpec.builder("World")
      .receiver(value.apiClassName)
      .addParameter(hostFactory)
      .returns(worldType)
      .apply {
        addStatement(
          "val %N = %T()",
          "world",
          value.hostClassName,
        )
        addStatement(
          "%N.%N = %N(%N.%N)",
          "world",
          "host",
          "hostFactory",
          "world",
          "guest",
        )
        addStatement(
          "return %N",
          "world",
        )
      }
      .build()
  }

  /** Generates the host side of [abiWorld], either for imports or exports. */
  context(collector: QualifiedSpecCollector)
  private fun generateWorld(abiWorld: AbiWorld) {
    val className = abiWorld.hostClassName
    val builder = TypeSpec.classBuilder(className)
      .addModifiers(KModifier.INTERNAL)
      .addSuperinterface(
        Symbols.Brevity.World.parameterizedBy(
          abiWorld.apiInterfaceName(Import),
          abiWorld.apiInterfaceName(Export),
        ),
      )

    val constructor = FunSpec.constructorBuilder()

    val bridgeValue = CodeBlock.of("%N", "bridge")
    builder.addProperty(
      PropertySpec.builder("bridge", Symbols.Brevity.HostBridge)
        .addModifiers(KModifier.PRIVATE)
        .initializer("%T()", Symbols.Brevity.HostBridge)
        .build(),
    )

    val importInterfaceName = abiWorld.apiInterfaceName(Import)
    builder.addProperty(
      PropertySpec.builder("host", importInterfaceName)
        .addModifiers(KModifier.LATEINIT, KModifier.OVERRIDE)
        .mutable(true)
        .build(),
    )

    val instanceValue = CodeBlock.of("%N", "instance")
    val initExportsBuilder = FunSpec.builder("initExports")
      .addParameter("instance", Symbols.ChicoryRuntime.Instance)
      .addModifiers(KModifier.OVERRIDE)
      .addStatement("this.%N.init(%L)", "bridge", instanceValue)

    val initImportsBuilder = FunSpec.builder("initImports")
      .addParameter("store", Symbols.ChicoryRuntime.Store)
      .addModifiers(KModifier.OVERRIDE)

    run {
      builder.addProperty(
        PropertySpec.builder("guest", abiWorld.apiInterfaceName(Export))
          .addModifiers(KModifier.OVERRIDE)
          .initializer("%T()", className.nestedClass(abiWorld.implementationName))
          .build(),
      )
      initExports(initExportsBuilder, builder, abiWorld.functions)
      initImports(initImportsBuilder, bridgeValue, abiWorld)
      builder.addType(generateExportsImplementation(abiWorld))
    }

    for (abiInterface in abiWorld.interfaces) {
      initExports(initExportsBuilder, builder, abiInterface.functions)
      initImports(initImportsBuilder, bridgeValue, abiInterface)
      if (abiInterface.orientation == Export) {
        builder.addType(generateExportsImplementation(abiInterface))
      }
    }

    for (abiResource in resources) {
      if (guestResourcesSupported) {
        initExports(initExportsBuilder, builder, abiResource.functions)
      }
      initImports(initImportsBuilder, bridgeValue, abiResource)
      if (guestResourcesSupported) {
        if (abiResource.orientation == Export) {
          builder.addType(generateExportsImplementation(abiResource))
        }
      }
    }

    collector.addType(
      className,
      builder
        .primaryConstructor(constructor.build())
        .addFunction(initExportsBuilder.build())
        .addFunction(initImportsBuilder.build())
        .build(),
    )
  }

  private fun initExports(
    initExportsBuilder: FunSpec.Builder,
    worldBuilder: TypeSpec.Builder,
    functions: List<AbiFunction>,
  ) {
    val instance = CodeBlock.of("%N", "instance")
    for (abiFunction in functions) {
      if (!abiFunction.isSupported) continue
      if (abiFunction.orientation != Export) continue

      val exportFunctionName = abiFunction.name.exportFunctionName
      worldBuilder.addProperty(
        PropertySpec.builder(exportFunctionName, Symbols.ChicoryRuntime.ExportFunction)
          .addModifiers(KModifier.INTERNAL, KModifier.LATEINIT)
          .mutable(true)
          .build(),
      )

      initExportsBuilder.addStatement(
        "this.%N = %L.export(%S)",
        exportFunctionName,
        instance,
        abiFunction.name,
      )
    }
  }

  private fun initImports(
    initImportsBuilder: FunSpec.Builder,
    bridgeValue: CodeBlock,
    parentService: AbiService,
  ) {
    val store = CodeBlock.of("%N", "store")
    for (abiFunction in parentService.functions) {
      if (!abiFunction.isSupported) continue
      if (abiFunction.orientation != Import) continue
      initImportsBuilder.addCode(
        declareHost(
          bridge = bridgeValue,
          store = store,
          parentService = parentService,
          abiFunction = abiFunction,
        ),
      )
    }
  }

  /** Generates one or two classes to implement [abiService] for this orientation. */
  private fun generateExportsImplementation(
    abiService: AbiService,
  ): TypeSpec {
    return TypeSpec.classBuilder(abiService.implementationName)
      .addModifiers(KModifier.INTERNAL, KModifier.INNER)
      .addSuperinterface(abiService.apiInterfaceName(Export))
      .primaryConstructor(
        FunSpec.constructorBuilder()
          .apply {
            if (abiService is AbiResource) {
              addParameter("id", INT)
            }
          }
          .build(),
      )
      .apply {
        if (abiService is AbiResource) {
          addProperty(
            PropertySpec.builder("id", INT)
              .addModifiers(KModifier.PRIVATE)
              .initializer("id")
              .build(),
          )
        }
        if (abiService is AbiWorld) {
          val worldClassName = abiService.hostClassName
          for (abiInterface in abiService.interfaces) {
            if (abiInterface.orientation != Export) continue
            addProperty(
              PropertySpec.builder(abiInterface.instanceName, abiInterface.apiClassName)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(
                  "%T()",
                  worldClassName.nestedClass(abiInterface.implementationName),
                )
                .build(),
            )
          }
        }

        val bridgeValue = CodeBlock.of("%N", "bridge")
        for (abiFunction in abiService.functions) {
          if (!abiFunction.isSupported) continue
          if (abiFunction.orientation != Export) continue
          addFunction(platformFunctionFactory.create(bridgeValue, abiFunction))
        }
      }
      .build()
  }

  /** Adds a host function using the Chicory API. */
  private fun declareHost(
    bridge: CodeBlock,
    store: CodeBlock,
    parentService: AbiService,
    abiFunction: AbiFunction,
  ): CodeBlock {
    val codeBuilder = CodeBuilder(
      bridge = bridge,
      platform = hostPlatform,
      nameAllocator = abiFunction.newNameAllocator(),
    )

    context(codeBuilder) {
      val function = ChicoryImportFunction(
        abiFunction,
        AbiLift(
          parentService = parentService,
          abiFunction = abiFunction,
          liftedFunction = CallLifted(hostPlatform, abiFunction),
        ),
      )
      val args = CodeBlockExpression(
        type = LONG_ARRAY,
        nameHint = "args",
        code = CodeBlock.of("%N", "args"),
        immediate = true,
      )
      val result = function.call(null, listOf(args))
      codeBuilder.add("return@%T %L", Symbols.ChicoryRuntime.WasmFunctionHandle, result.code)
    }

    return addChicoryFunction(
      store = store,
      moduleName = abiFunction.name.moduleName,
      abiName = abiFunction.name.abiName,
      parameterTypes = abiFunction.loweredParameterTypes,
      returnType = abiFunction.result.loweredType,
      body = codeBuilder.build(),
    )
  }

  private fun addChicoryFunction(
    store: CodeBlock,
    moduleName: String?,
    abiName: String,
    parameterTypes: List<CoreType>,
    returnType: CoreType?,
    body: CodeBlock,
  ): CodeBlock {
    return CodeBlock.of(
      """
      |%L.addFunction(
      |  %T(
      |    %L,
      |    %S,
      |    %T.of(
      |      listOf(%L),
      |      listOf(%L),
      |    ),
      |    %T { instance, args ->
      |      ⇥⇥⇥%L⇤⇤⇤    },
      |  )
      |)
      |
      """.trimMargin(),
      store,
      Symbols.ChicoryRuntime.HostFunction,
      moduleName?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"),
      abiName,
      Symbols.ChicoryRuntime.FunctionType,
      parameterTypes.joinToCode { it.valType },
      returnType?.valType ?: CodeBlock.of(""),
      Symbols.ChicoryRuntime.WasmFunctionHandle,
      body,
    )
  }

  private fun defaultHostFactory(abiWorld: AbiWorld): CodeBlock? {
    if (abiWorld.hasMembers(Import)) return null

    return CodeBlock.of(
      "{ %N -> object : %T {} }",
      "guest",
      abiWorld.apiInterfaceName(Import),
    )
  }

  private fun AbiWorld.hasMembers(orientation: Orientation): Boolean =
    memberFunctions(orientation).isNotEmpty() || interfaces(orientation).isNotEmpty()
}

private val AbiWorld.hostClassName: ClassName
  get() = apiClassName.wrap(prefix = "Host")

/** Returns a name for the generated implementation of this service. */
private val AbiService.implementationName: String
  get() {
    return when (this) {
      is AbiInterface -> "Real${serviceName.name.upperCamelCase}"
      is AbiResource -> "Real${type.name.upperCamelCase}"
      is AbiWorld -> "RealGuest"
    }
  }

private fun ClassName.wrap(
  prefix: Any = "",
  suffix: Any = "",
) = ClassName(packageName, "$prefix${simpleNames.joinToString(separator = "")}$suffix")
