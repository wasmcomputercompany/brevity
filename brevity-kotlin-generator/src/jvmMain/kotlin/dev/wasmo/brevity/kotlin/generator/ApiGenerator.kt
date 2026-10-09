package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.Documentable
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.ir.IrDeclaration
import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.expressions.AbiFunction
import dev.wasmo.brevity.kotlin.expressions.AbiResource
import dev.wasmo.brevity.kotlin.expressions.ApiFunctionFactory
import dev.wasmo.brevity.kotlin.expressions.FunctionParent

class ApiGenerator(
  private val kotlinMapper: KotlinMapper,
  private val abiFunctionFactory: AbiFunction.Factory,
  private val apiFunctionFactory: ApiFunctionFactory,
  private val packages: List<IrWitPackage>,
  private val resources: List<AbiResource>,
) {
  fun generate(): List<QualifiedSpec> {
    val result = mutableListOf<QualifiedSpec>()

    for (service in packages.flatMap { it.services }) {
      val serviceName = service.serviceName.kotlinApi
      result.collect(
        sourceSet = QualifiedSpec.SourceSet.CommonMain,
        locations = setOf(service.location),
        packageName = serviceName.packageName,
        fileName = serviceName.simpleName,
      ) {
        generateApi(service)
      }
    }
    for (resource in resources) {
      if (resource.orientation != Orientation.Export) continue // Either one, but only once.
      val resourceName = resource.apiClassName
      result.collect(
        sourceSet = QualifiedSpec.SourceSet.CommonMain,
        locations = setOf(resource.location),
        packageName = resourceName.packageName,
        fileName = resourceName.simpleName,
      ) {
        generateResource(resource)
      }
    }

    result += kotlinMapper.adapterInterfaces()

    return result
  }

  private fun <T : Documentable.Builder<*>> T.setDeclaration(
    declaration: IrDeclaration? = null,
  ): T = apply {
    val documentation = declaration?.documentation
    if (documentation != null) {
      addKdoc(documentation.content.trimIndent())
    }
  }

  context(collector: QualifiedSpecCollector)
  private fun generateRecord(value: IrRecord) {
    val className = kotlinMapper.getAbiClassName(value.type)
    collector.addType(
      className = className,
      type = TypeSpec.classBuilder(className)
        .addModifiers(KModifier.DATA)
        .setDeclaration(value)
        .apply {
          val constructorBuilder = FunSpec.constructorBuilder()

          for (field in value.fields) {
            val name = field.kotlinName
            val fieldType = kotlinMapper.get(field.type)
            val parameter = ParameterSpec.builder(name, fieldType)
              .build()
            constructorBuilder.addParameter(parameter)

            addProperty(
              PropertySpec.builder(name, fieldType)
                .initializer("%N", parameter)
                .setDeclaration(field)
                .build(),
            )
          }

          primaryConstructor(constructorBuilder.build())
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateResource(value: AbiResource) {
    val className = kotlinMapper.getAbiClassName(value.type)
    collector.addType(
      className = className,
      type = TypeSpec.interfaceBuilder(className)
        .setDeclaration(value.irResource)
        .addSuperinterface(Symbols.Brevity.Resource)
        .apply {
          for (function in value.functions) {
            if (!function.isSupported) continue
            // Don't override close(), it's inherited from the 'Resource' supertype.
            if (function.name is FunctionName.ResourceDrop) continue
            addFunction(apiFunctionFactory.create(function))
          }
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateVariant(value: IrVariant) {
    val className = kotlinMapper.getAbiClassName(value.type)
    collector.addType(
      className,
      TypeSpec.interfaceBuilder(className)
        .addModifiers(KModifier.SEALED)
        .setDeclaration(value)
        .apply {
          for (case in value.cases) {
            val type = case.type
            if (type != null) {
              val caseType = kotlinMapper.get(type)
              addType(
                TypeSpec.classBuilder(case.kotlinName)
                  .addModifiers(KModifier.VALUE)
                  .addAnnotation(JvmInline::class)
                  .addSuperinterface(className)
                  .primaryConstructor(
                    FunSpec.constructorBuilder()
                      .addParameter("value", caseType)
                      .build(),
                  )
                  .addProperty(
                    PropertySpec.builder("value", caseType)
                      .initializer("%N", "value")
                      .build(),
                  )
                  .setDeclaration(case)
                  .build(),
              )
            } else {
              addType(
                TypeSpec.objectBuilder(case.kotlinName)
                  .addModifiers(KModifier.DATA)
                  .addSuperinterface(className)
                  .setDeclaration(case)
                  .build(),
              )
            }
          }
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateEnum(value: IrEnum) {
    val className = kotlinMapper.getAbiClassName(value.type)
    collector.addType(
      className = className,
      type = TypeSpec.enumBuilder(className)
        .setDeclaration(value)
        .apply {
          for (case in value.cases) {
            addEnumConstant(
              case.kotlinName,
              TypeSpec.anonymousClassBuilder()
                .setDeclaration(case)
                .build(),
            )
          }
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateTypeAlias(value: IrTypeAlias) {
    val className = kotlinMapper.getAbiClassName(value.type)
    collector.addType(
      className = className,
      type = TypeSpec.classBuilder(className)
        .addModifiers(KModifier.VALUE)
        .addAnnotation(JvmInline::class)
        .setDeclaration(value)
        .apply {
          val targetType = kotlinMapper.get(value.target)
          val parameter = ParameterSpec.builder("value", targetType)
            .build()

          primaryConstructor(
            FunSpec.constructorBuilder()
              .addParameter(parameter)
              .build(),
          )

          addProperty(
            PropertySpec.builder("value", targetType)
              .initializer("%N", parameter)
              .build(),
          )
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateFlags(value: IrFlags) {
    val className = kotlinMapper.getAbiClassName(value.type)
    collector.addType(
      className = className,
      type = TypeSpec.classBuilder(className)
        .addModifiers(KModifier.DATA)
        .setDeclaration(value)
        .apply {
          val constructorBuilder = FunSpec.constructorBuilder()

          for (field in value.flags) {
            val parameter = ParameterSpec.builder(field.kotlinName, BOOLEAN)
              .build()
            constructorBuilder.addParameter(parameter)
            addProperty(
              PropertySpec.builder(field.kotlinName, BOOLEAN)
                .initializer("%N", parameter)
                .setDeclaration(field)
                .build(),
            )
          }

          primaryConstructor(constructorBuilder.build())
        }
        .build(),
    )
  }

  context(collector: QualifiedSpecCollector)
  private fun generateApi(value: IrWitPackage.Service) {
    if (!value.hasInstanceMembers && value.types.isEmpty()) return

    val typeName = value.serviceName.kotlinApi

    val builder = when {
      !value.hasInstanceMembers || value is IrWorld -> TypeSpec.objectBuilder(typeName)
      else -> TypeSpec.interfaceBuilder(typeName)
    }

    value.documentation?.let {
      builder.addKdoc(it.content.trimIndent())
    }

    for (type in value.types) {
      when (type) {
        is IrEnum -> generateEnum(type)
        is IrFlags -> generateFlags(type)
        is IrRecord -> generateRecord(type)
        is IrResource -> {} // Handled by generateResource().
        is IrTypeAlias -> generateTypeAlias(type)
        is IrVariant -> generateVariant(type)
      }
    }

    when (value) {
      is IrInterface -> {
        val parent = FunctionParent.Interface(
          orientation = Orientation.Export,
          serviceName = value.serviceName,
          instanceName = "null", // Declare only.
        )
        for (function in value.functions) {
          val abiFunction = abiFunctionFactory.create(parent, function)
          builder.addFunction(apiFunctionFactory.create(abiFunction))
        }
      }

      is IrWorld -> {
        generateExternalApis(
          parent = FunctionParent.World(
            orientation = Orientation.Export,
            serviceName = value.serviceName,
          ),
          value = value.guestApis,
        )
        generateExternalApis(
          parent = FunctionParent.World(
            orientation = Orientation.Import,
            serviceName = value.serviceName,
          ),
          value = value.hostApis,
        )
      }
    }

    collector.addType(typeName, builder.build())
  }

  context(collector: QualifiedSpecCollector)
  private fun generateExternalApis(
    parent: FunctionParent,
    value: ExternalApis,
  ) {
    collector.addType(
      className = value.type,
      type = TypeSpec.interfaceBuilder(value.type)
        .apply {
          for (item in value.items) {
            when (item) {
              is IrExternalApi -> addProperty(item.instanceName, item.serviceName.kotlinApi)
              is IrFunction -> {
                val abiFunction = abiFunctionFactory.create(parent, item)
                addFunction(apiFunctionFactory.create(abiFunction))
              }
            }
          }
        }
        .build(),
    )
  }
}
