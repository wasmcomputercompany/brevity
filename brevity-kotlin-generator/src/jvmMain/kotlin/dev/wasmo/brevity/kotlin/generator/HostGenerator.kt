package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
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
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory

class HostGenerator(
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

        result.collect(
          sourceSet = QualifiedSpec.SourceSet.JvmMain,
          locations = setOf(type.location),
          packageName = className.packageName,
          fileName = className.simpleNames.joinToString(separator = "") + "Host",
        ) {
          for (encoder in declaredTypeEncodersGenerator.generate(type, roles)) {
            addFunction(encoder)
          }
        }
      }

      val className = service.serviceName.kotlinApi
      result.collect(
        packageName = className.packageName,
        locations = setOf(service.location),
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

  private fun QualifiedSpecCollector.generateWorldFactoryFunction(value: IrWorld) {
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

    val function = FunSpec.builder("World")
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
    addFunction(function)
  }

  private fun QualifiedSpecCollector.generateBridge(value: IrWitPackage.Service) {
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
              if (hostApis != null) {
                val receiver = Receiver.Instance(
                  codeBlock = CodeBlock.of("%N", "host"),
                )
                initImports(
                  bridge = CodeBlock.of("%N", "bridge"),
                  store = CodeBlock.of("%N", "store"),
                  receiver = receiver,
                  value = hostApis,
                )
              }
              for ((typeName, entry) in roleTracker.types) {
                initImports(
                  typeName = typeName,
                  bridge = CodeBlock.of("%N", "bridge"),
                  store = CodeBlock.of("%N", "store"),
                  value = entry,
                )
              }
            }
            .build(),
        )

        if (guestApis != null) {
          builder.addExternalApis(guestApis)
        }

        if (hostApis != null) {
          builder.addExternalApis(hostApis)
        }
      }

      is IrInterface -> {
        for (item in value.functions) {
          builder.addFunction(
            HostFunctionFactory
              (encoderFactory, item, CodeBlock.of("%N", "bridge")).callGuest(),
          )
          builder.addProperty(
            PropertySpec.builder(item.kotlinName, Symbols.ChicoryRuntime.ExportFunction)
              .addModifiers(KModifier.INTERNAL, KModifier.LATEINIT)
              .mutable(true)
              .build(),
          )
        }
      }
    }

    builder.primaryConstructor(constructor.build())

    addType(value.serviceName.bridgeType, builder.build())
  }

  private fun TypeSpec.Builder.addExternalApis(externalApis: ExternalApis) {
    addType(
      TypeSpec.classBuilder(externalApis.bridgeType)
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
          HostFunctionFactory(encoderFactory, item, CodeBlock.of("%N", "bridge")).callGuest(),
        )
        addProperty(
          PropertySpec.builder(item.kotlinName, Symbols.ChicoryRuntime.ExportFunction)
            .addModifiers(KModifier.INTERNAL, KModifier.LATEINIT)
            .mutable(true)
            .build(),
        )
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
          addStatement(
            "%L.%N = %L.export(%S)",
            guest,
            item.kotlinName,
            instance,
            item.functionName,
          )
        }

        is IrExternalApi -> {
          val type = declarationIndex[item.serviceName] as IrInterface
          for (function in type.functions) {
            addStatement(
              "%L.%N.%N = %L.export(%S)",
              guest,
              item.instanceName,
              function.kotlinName,
              instance,
              function.functionName,
            )
          }
        }
      }
    }
  }

  private fun FunSpec.Builder.initImports(
    typeName: TypeName.Declared,
    bridge: CodeBlock,
    store: CodeBlock,
    value: RoleTracker.Entry,
  ) {
    when (val typeDeclaration = declarationIndex[typeName]) {
      is IrResource -> {
        val receiver = Receiver.Id(
          bridge = bridge,
          type = typeDeclaration.type.kotlinApi,
        )

        for (function in typeDeclaration.functions) {
          if (value.host) {
            addCode(
              HostFunctionFactory(encoderFactory, function, bridge).declareHost(
                store,
                receiver,
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
    receiver: Receiver.Instance,
    value: ExternalApis,
  ) {
    for (item in value.items) {
      when (item) {
        is IrFunction -> {
          addCode(
            HostFunctionFactory(encoderFactory, item, bridge).declareHost(
              store = store,
              receiver = receiver,
            ),
          )
        }

        is IrExternalApi -> {
          val type = declarationIndex[item.serviceName] as IrInterface
          for (function in type.functions) {
            addCode(
              HostFunctionFactory(encoderFactory, function, bridge).declareHost(
                store = store,
                receiver = Receiver.Instance(
                  CodeBlock.of("%L.%N", receiver.codeBlock, item.instanceName),
                ),
              ),
            )
          }
        }
      }
    }
  }

  internal sealed interface Receiver {
    data class Instance(
      val codeBlock: CodeBlock,
    ) : Receiver

    data class Id(
      val bridge: CodeBlock,
      val type: ClassName,
    ) : Receiver {
      val name: String
        get() = "self"

      fun codeBlock(id: CodeBlock) = CodeBlock.of(
        "%L.%M<%T>(%L)",
        bridge,
        Symbols.Brevity.HostBridgeGet,
        type,
        id,
      )
    }
  }
}
