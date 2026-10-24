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
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Orientation
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
import dev.wasmo.brevity.kotlin.abi.ChicoryImportFunction
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.valType
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression

class HostGenerator(
  private val platformFunctionFactory: PlatformFunctionFactory,
  private val kotlinMapper: KotlinMapper,
  private val hostPlatform: HostPlatform,
  private val worlds: List<AbiWorld>,
) {
  fun generate(): List<QualifiedSpec> {
    val result = mutableListOf<QualifiedSpec>()

    for (abiWorld in worlds) {
      val className = abiWorld.apiClassName
      val encoderFunctionCollector = EncoderFunctionCollector(
        kotlinMapper = kotlinMapper,
        qualifiedSpecCollector = result.collector(
          sourceSet = QualifiedSpec.SourceSet.JvmMain,
          className = className,
        ),
        world = abiWorld,
      )
      val collector = result.collector(
        sourceSet = QualifiedSpec.SourceSet.JvmMain,
        packageName = className.packageName,
        locations = setOf(abiWorld.location),
        optIns = hostOptIns,
        fileName = "${className.simpleName}Host",
      )
      context(collector) {
        generateWorldFactoryFunction(abiWorld)
        generateWorld(encoderFunctionCollector, abiWorld)
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
  private fun generateWorld(
    encoderFunctionCollector: EncoderFunctionCollector,
    abiWorld: AbiWorld,
  ) {
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
        .initializer(
          buildCodeBlock {
            add("%T()", Symbols.Brevity.HostBridge)
            val allResources = (abiWorld.resources + abiWorld.interfaces.flatMap { it.resources })
              .filter { it.orientation == Export }

            if (allResources.isNotEmpty()) {
              beginControlFlow(".apply")
              endControlFlow()
            }
          },
        )
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
      initImports(
        encoderFunctionCollector = encoderFunctionCollector,
        initImportsBuilder = initImportsBuilder,
        bridgeValue = bridgeValue,
        worldClassName = className,
        parentService = abiWorld,
      )
      builder.addType(
        generateExportsImplementation(
          encoderFunctionCollector = encoderFunctionCollector,
          abiService = abiWorld,
        ),
      )
    }

    fun generateResource(abiResource: AbiResource) {
      initExports(initExportsBuilder, builder, abiResource.functions)
      initImports(
        encoderFunctionCollector = encoderFunctionCollector,
        initImportsBuilder = initImportsBuilder,
        bridgeValue = bridgeValue,
        worldClassName = className,
        parentService = abiResource,
      )
      if (abiResource.orientation == Export) {
        builder.addType(
          generateExportsImplementation(
            encoderFunctionCollector = encoderFunctionCollector,
            abiService = abiResource,
          ),
        )
      }
    }

    fun generateInterface(abiInterface: AbiInterface) {
      initExports(initExportsBuilder, builder, abiInterface.functions)
      initImports(
        encoderFunctionCollector = encoderFunctionCollector,
        initImportsBuilder = initImportsBuilder,
        bridgeValue = bridgeValue,
        worldClassName = className,
        parentService = abiInterface,
      )
      if (abiInterface.orientation == Export) {
        builder.addType(
          generateExportsImplementation(
            encoderFunctionCollector = encoderFunctionCollector,
            abiService = abiInterface,
          ),
        )
      }
      for (abiResource in abiInterface.resources) {
        generateResource(abiResource)
      }
    }

    for (abiInterface in abiWorld.interfaces) {
      generateInterface(abiInterface)
    }
    for (abiResource in abiWorld.resources) {
      generateResource(abiResource)
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
    encoderFunctionCollector: EncoderFunctionCollector,
    initImportsBuilder: FunSpec.Builder,
    bridgeValue: CodeBlock,
    worldClassName: ClassName,
    parentService: AbiService,
  ) {
    val store = CodeBlock.of("%N", "store")
    for (abiFunction in parentService.functions) {
      if (!abiFunction.isSupported) continue
      if (abiFunction.orientation != Import) continue
      initImportsBuilder.addCode(
        declareHost(
          encoderFunctionCollector = encoderFunctionCollector,
          bridge = bridgeValue,
          worldClassName = worldClassName,
          store = store,
          parentService = parentService,
          abiFunction = abiFunction,
        ),
      )
    }
  }

  /** Generates one or two classes to implement [abiService] for this orientation. */
  private fun generateExportsImplementation(
    encoderFunctionCollector: EncoderFunctionCollector,
    abiService: AbiService,
  ): TypeSpec {
    return TypeSpec.classBuilder(abiService.implementationName)
      .addModifiers(KModifier.INTERNAL, KModifier.INNER)
      .primaryConstructor(
        FunSpec.constructorBuilder()
          .apply {
            if (abiService is AbiResource) {
              addParameter("rep", INT)
            }
          }
          .build(),
      )
      .apply {
        when (abiService) {
          is AbiWorld -> {
            addSuperinterface(abiService.apiInterfaceName(Export))
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

          is AbiInterface -> {
            addSuperinterface(abiService.apiInterfaceName(Export))
          }

          is AbiResource -> {
            superclass(abiService.apiInterfaceName(Export))
            addProperty(
              PropertySpec.builder("rep", INT)
                .addModifiers(KModifier.PRIVATE)
                .initializer("rep")
                .build(),
            )
          }
        }

        val bridgeValue = CodeBlock.of("%N", "bridge")
        for (abiFunction in abiService.functions) {
          if (!abiFunction.isSupported) continue
          if (abiFunction.orientation != Export) continue
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
      .build()
  }

  /** Adds a host function using the Chicory API. */
  private fun declareHost(
    encoderFunctionCollector: EncoderFunctionCollector,
    bridge: CodeBlock,
    store: CodeBlock,
    worldClassName: ClassName,
    parentService: AbiService,
    abiFunction: AbiFunction,
  ): CodeBlock {
    val codeBuilder = CodeBuilder(
      bridge = bridge,
      platform = hostPlatform,
      nameAllocator = abiFunction.newNameAllocator(),
    )

    val resourceImplementation = when {
      abiFunction.isResource -> worldClassName.nestedClass(parentService.implementationName)
      abiFunction.name is FunctionName.ResourceNew ->
        worldClassName.nestedClass(abiFunction.name.resourceType.handleName)

      else -> null
    }

    context(codeBuilder) {
      val function = ChicoryImportFunction(
        abiFunction,
        AbiLift(
          functionEncoder = FunctionEncoder(
            collector = encoderFunctionCollector,
            service = parentService,
            function = abiFunction,
          ),
          resourceImplementation = resourceImplementation,
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
      returnType = abiFunction.loweredReturnType,
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
    interfaces(orientation).isNotEmpty() ||
      functions.any { it.orientation == orientation && !it.isSynthetic }
}

private val AbiWorld.hostClassName: ClassName
  get() = apiClassName.wrap(prefix = "Host")

/** Returns a name for the generated implementation of this service. */
private val AbiService.implementationName: String
  get() {
    return when (this) {
      is AbiInterface -> "Real${serviceName.name.upperCamelCase}"
      is AbiResource -> type.handleName
      is AbiWorld -> "RealGuest"
    }
  }

private val TypeName.Declared.handleName: String
  get() = "${name.upperCamelCase}Handle"

private fun ClassName.wrap(
  prefix: Any = "",
  suffix: Any = "",
) = ClassName(packageName, "$prefix${simpleNames.joinToString(separator = "")}$suffix")

private val hostOptIns = setOf(
  Symbols.Brevity.BrevityInternalApi,
)
