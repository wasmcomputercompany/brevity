package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Orientation.GuestCallsHost
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Orientation.HostCallsGuest
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Receiver

private val hostOptIns = setOf(
  Symbols.Brevity.BrevityInternalApi,
)

class HostGenerator(
  private val hostFunctionFactory: HostFunctionFactory,
  private val bridgeFunctionFactory: BridgeFunction.Factory,
  private val declarationIndex: DeclarationIndex,
  private val declaredTypeEncodersGenerator: DeclaredTypeEncodersGenerator,
  private val roleTracker: RoleTracker,
  private val packages: List<IrWitPackage>,
) {
  /** Receives inbound calls on this host. */
  private val hostInstance = Receiver.InboundInstance(
    codeBlock = CodeBlock.of("%N", "host"),
  )

  fun generate(): List<QualifiedSpec> {
    val result = mutableListOf<QualifiedSpec>()

    for (service in packages.flatMap { it.services }) {
      for (type in service.types) {
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

      val className = service.serviceName.kotlinApi
      result.collect(
        packageName = className.packageName,
        locations = setOf(service.location),
        optIns = hostOptIns,
        fileName = "${className.simpleName}Host",
        sourceSet = QualifiedSpec.SourceSet.JvmMain,
      ) {
        if (service is IrWorld) {
          generateWorldFactoryFunction(service)
        }

        generateBridge(service)
      }
    }

    return result
  }

  context(collector: QualifiedSpecCollector)
  private fun generateWorldFactoryFunction(value: IrWorld) {
    val guestApis = value.guestApis
    val hostApis = value.hostApis
    if (guestApis == null && hostApis == null) return

    // The implemented World interface uses the interface types; everything else uses the
    // implementation types.
    val worldType = Symbols.Brevity.World.parameterizedBy(
      value.hostApis?.type ?: UNIT,
      value.guestApis?.type ?: UNIT,
    )

    val hostFactory = ParameterSpec.builder(
      "hostFactory",
      LambdaTypeName.get(
        parameters = listOf(ParameterSpec.unnamed(guestApis?.type ?: UNIT)),
        returnType = value.hostApis?.type ?: UNIT,
      ),
    ).build()

    collector += FunSpec.builder("World")
      .receiver(value.serviceName.kotlinApi)
      .addParameter(hostFactory)
      .returns(worldType)
      .apply {
        addStatement("val %N = %T()", "bridge", Symbols.Brevity.HostBridge)

        if (guestApis != null) {
          addStatement("val %N = %T(%N)", "guest", guestApis.bridgeType, "bridge")
        } else {
          addStatement("val %N = %T", "guest", UNIT)
        }

        addStatement("val %N = %N(%N)", "host", "hostFactory", "guest")

        addStatement(
          "return %T(%N, %N, %N)",
          value.serviceName.bridgeType,
          "bridge",
          "guest",
          "host",
        )
      }
      .build()
  }

  context(collector: QualifiedSpecCollector)
  private fun generateBridge(value: IrWitPackage.Service) {
    if (!value.hasInstanceMembers) return

    val builder = TypeSpec.classBuilder(value.serviceName.bridgeType)
      .addModifiers(KModifier.INTERNAL)

    if (value !is IrWorld) {
      builder.addSuperinterface(value.serviceName.kotlinApi)
    }

    val constructor = FunSpec.constructorBuilder()

    constructor.addParameter("bridge", Symbols.Brevity.HostBridge)
    builder.addProperty(
      PropertySpec.builder("bridge", Symbols.Brevity.HostBridge)
        .addModifiers(KModifier.PRIVATE)
        .initializer("bridge")
        .build(),
    )

    when (value) {
      is IrWorld -> {
        val guestApis = value.guestApis
        val guestType = guestApis?.bridgeType ?: UNIT
        val hostApis = value.hostApis
        val hostType = hostApis?.type ?: UNIT

        builder.addSuperinterface(
          Symbols.Brevity.World.parameterizedBy(
            value.hostApis?.type ?: UNIT,
            value.guestApis?.type ?: UNIT,
          ),
        )

        constructor.addParameter("guest", guestType)
        builder.addProperty(
          PropertySpec.builder("guest", guestType)
            .addModifiers(KModifier.OVERRIDE)
            .initializer("guest")
            .build(),
        )

        constructor.addParameter("host", hostType)
        builder.addProperty(
          PropertySpec.builder("host", hostType)
            .addModifiers(KModifier.OVERRIDE)
            .initializer("host")
            .build(),
        )

        builder.addFunction(
          FunSpec.builder("initExports")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("instance", Symbols.ChicoryRuntime.Instance)
            .apply {
              addStatement("this.%N.init(%N)", "bridge", "instance")
              if (guestApis != null) {
                initExports(
                  guest = CodeBlock.of("%N", "guest"),
                  instance = CodeBlock.of("%N", "instance"),
                  value = guestApis,
                )
              }
            }
            .build(),
        )

        builder.addFunction(
          FunSpec.builder("initImports")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("store", Symbols.ChicoryRuntime.Store)
            .apply {
              val bridge = CodeBlock.of("%N", "bridge")
              val store = CodeBlock.of("%N", "store")
              if (hostApis != null) {
                initImports(
                  bridge = bridge,
                  store = store,
                  value = hostApis,
                )
              }
              if (guestApis != null) {
                initImportCallbacks(
                  bridge = bridge,
                  store = store,
                  value = guestApis,
                )
              }
              for ((typeName, entry) in roleTracker.types) {
                initImports(
                  typeName = typeName,
                  bridge = bridge,
                  store = store,
                  value = entry,
                )
              }
            }
            .build(),
        )

        if (guestApis != null) {
          generateExternalApis(guestApis)
        }

        if (hostApis != null) {
          generateExternalApis(hostApis)
        }
      }

      is IrInterface -> {
        for (item in value.functions) {
          builder.addFunction(
            hostFunctionFactory.callGuest(
              bridge = CodeBlock.of("%N", "bridge"),
              function = bridgeFunctionFactory.create(
                Receiver.OutboundInstance,
                HostCallsGuest,
                item,
              ),
            ),
          )
          builder.addProperties(exportProperties(item))
        }
      }
    }

    builder.primaryConstructor(constructor.build())

    collector.addType(value.serviceName.bridgeType, builder.build())
  }

  context(collector: QualifiedSpecCollector)
  private fun generateExternalApis(externalApis: ExternalApis) {
    collector.addType(
      className = externalApis.bridgeType,
      type = TypeSpec.classBuilder(externalApis.bridgeType)
        .addModifiers(KModifier.INTERNAL)
        .addSuperinterface(externalApis.type)
        .primaryConstructor(
          FunSpec.constructorBuilder()
            .addParameter("bridge", Symbols.Brevity.HostBridge)
            .build(),
        )
        .addProperty(
          PropertySpec.builder("bridge", Symbols.Brevity.HostBridge)
            .addModifiers(KModifier.PRIVATE)
            .initializer("bridge")
            .build(),
        )
        .apply {
          for (item in externalApis.items) {
            addExternalApisItem(externalApis, item)
          }
        }
        .build(),
    )
  }

  private fun TypeSpec.Builder.addExternalApisItem(
    externalApis: ExternalApis,
    item: IrWorld.Api,
  ) {
    when (item) {
      is IrExternalApi -> {
        addProperty(
          PropertySpec.builder(item.instanceName, item.serviceName.bridgeType)
            .addModifiers(KModifier.OVERRIDE)
            .initializer("%T(%N)", item.serviceName.bridgeType, "bridge")
            .build(),
        )
      }

      is IrFunction -> {
        addFunction(
          hostFunctionFactory.callGuest(
            bridge = CodeBlock.of("%N", "bridge"),
            function = bridgeFunctionFactory.create(
              Receiver.OutboundInstance,
              HostCallsGuest,
              item,
            ),
          ),
        )
        addProperties(exportProperties(item))
      }
    }
  }

  private fun FunSpec.Builder.initExports(
    guest: CodeBlock,
    instance: CodeBlock,
    value: ExternalApis,
  ) {
    for (item in value.items) {
      when (item) {
        is IrFunction -> {
          val owner = CodeBlock.of("%L", guest)
          saveExports(owner, instance, item)
        }

        is IrExternalApi -> {
          val type = declarationIndex[item.serviceName] as IrInterface
          val owner = CodeBlock.of("%L.%N", guest, item.instanceName)
          for (function in type.functions) {
            saveExports(owner, instance, function)
          }
        }
      }
    }
  }

  private fun FunSpec.Builder.saveExports(
    owner: CodeBlock,
    instance: CodeBlock,
    item: IrFunction,
  ) {
    when {
      item.async -> {
        saveExport(owner, instance, FunctionName.AsyncLift(item.functionName))
        saveExport(owner, instance, FunctionName.AsyncLiftCallback(item.functionName))
      }

      else -> {
        saveExport(owner, instance, item.functionName)
      }
    }
  }

  private fun FunSpec.Builder.saveExport(
    owner: CodeBlock,
    instance: CodeBlock,
    functionName: FunctionName,
  ) {
    addStatement(
      "%L.%N = %L.export(%S)",
      owner,
      functionName.kotlinName,
      instance,
      functionName,
    )
  }

  private fun exportProperties(item: IrFunction): List<PropertySpec> {
    return when {
      item.async -> listOf(
        exportProperty(FunctionName.AsyncLift(item.functionName)),
        exportProperty(FunctionName.AsyncLiftCallback(item.functionName)),
      )

      else -> listOf(exportProperty(item.functionName))
    }
  }

  private fun exportProperty(name: FunctionName): PropertySpec {
    return PropertySpec.builder(name.kotlinName, Symbols.ChicoryRuntime.ExportFunction)
      .addModifiers(KModifier.INTERNAL, KModifier.LATEINIT)
      .mutable(true)
      .build()
  }

  private fun FunSpec.Builder.initImports(
    typeName: TypeName.Declared,
    bridge: CodeBlock,
    store: CodeBlock,
    value: RoleTracker.Entry,
  ) {
    when (val typeDeclaration = declarationIndex[typeName]) {
      is IrResource -> {
        for (function in typeDeclaration.functions) {
          if (value.host) {
            val id = Receiver.Id(type = typeDeclaration.type)
            addCode(
              hostFunctionFactory.declareHost(
                bridge = bridge,
                store = store,
                function = bridgeFunctionFactory.create(
                  receiver = id,
                  orientation = GuestCallsHost,
                  value = function,
                ),
              ),
            )
          }
        }
      }

      else -> {} // TODO
    }
  }

  private fun FunSpec.Builder.initImports(
    bridge: CodeBlock,
    store: CodeBlock,
    value: ExternalApis,
  ) {
    for (item in value.items) {
      when (item) {
        is IrFunction -> {
          addCode(
            hostFunctionFactory.declareHost(
              bridge = bridge,
              store = store,
              function = bridgeFunctionFactory.create(
                receiver = hostInstance,
                orientation = GuestCallsHost,
                value = item,
              ),
            ),
          )
        }

        is IrExternalApi -> {
          val type = declarationIndex[item.serviceName] as IrInterface
          for (function in type.functions) {
            val inboundInstance = Receiver.InboundInstance(
              CodeBlock.of("%L.%N", hostInstance.codeBlock, item.instanceName),
            )
            addCode(
              hostFunctionFactory.declareHost(
                bridge = bridge,
                store = store,
                function = bridgeFunctionFactory.create(
                  receiver = inboundInstance,
                  orientation = GuestCallsHost,
                  value = function,
                ),
              ),
            )
          }
        }
      }
    }
  }

  private fun FunSpec.Builder.initImportCallbacks(
    bridge: CodeBlock,
    store: CodeBlock,
    value: ExternalApis,
  ) {
    for (item in value.items) {
      when (item) {
        is IrFunction -> {
          if (item.async) {
            addCode(
              hostFunctionFactory.declareHost(
                bridge = bridge,
                store = store,
                function = bridgeFunctionFactory.taskReturn(
                  receiver = hostInstance,
                  item,
                ),
              ),
            )
          }
        }

        is IrExternalApi -> {
          val type = declarationIndex[item.serviceName] as IrInterface
          for (function in type.functions) {
            if (function.async) {
              val inboundInstance = Receiver.InboundInstance(
                CodeBlock.of("%L.%N", hostInstance.codeBlock, item.instanceName),
              )
              addCode(
                hostFunctionFactory.declareHost(
                  bridge = bridge,
                  store = store,
                  function = bridgeFunctionFactory.taskReturn(
                    inboundInstance,
                    function,
                  ),
                ),
              )
            }
          }
        }
      }
    }
  }
}
