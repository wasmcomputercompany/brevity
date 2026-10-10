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
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.expressions.AbiInterface
import dev.wasmo.brevity.kotlin.expressions.AbiResource
import dev.wasmo.brevity.kotlin.expressions.AbiService
import dev.wasmo.brevity.kotlin.expressions.AbiWorld
import dev.wasmo.brevity.kotlin.expressions.ApiFunctionFactory

class ApiGenerator(
  private val kotlinMapper: KotlinMapper,
  private val apiFunctionFactory: ApiFunctionFactory,
  private val worlds: List<AbiWorld>,
  private val interfaces: List<AbiInterface>,
  private val resources: List<AbiResource>,
) {
  fun generate(): List<QualifiedSpec> {
    val result = mutableListOf<QualifiedSpec>()

    for (world in worlds) {
      val apiClassName = world.apiClassName
      result.collect(
        sourceSet = QualifiedSpec.SourceSet.CommonMain,
        locations = setOf(world.location),
        packageName = apiClassName.packageName,
        fileName = apiClassName.simpleName,
      ) {
        generateServiceInterface(world)
        generateWorldInterface(world, Orientation.Export)
        generateWorldInterface(world, Orientation.Import)
      }
    }
    for (abiInterface in interfaces) {
      val apiClassName = abiInterface.apiClassName
      result.collect(
        sourceSet = QualifiedSpec.SourceSet.CommonMain,
        locations = setOf(abiInterface.location),
        packageName = apiClassName.packageName,
        fileName = apiClassName.simpleName,
      ) {
        generateServiceInterface(abiInterface)
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

  context(collector: QualifiedSpecCollector)
  private fun generateRecord(irRecord: IrRecord) {
    val className = kotlinMapper.getAbiClassName(irRecord.type)
    collector.addType(
      className = className,
      type = TypeSpec.classBuilder(className)
        .addModifiers(KModifier.DATA)
        .setDeclaration(irRecord)
        .apply {
          val constructorBuilder = FunSpec.constructorBuilder()

          for (field in irRecord.fields) {
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
  private fun generateResource(abiResource: AbiResource) {
    val className = kotlinMapper.getAbiClassName(abiResource.type)
    collector.addType(
      className = className,
      type = TypeSpec.interfaceBuilder(className)
        .setDeclaration(abiResource.irDeclaration)
        .addSuperinterface(Symbols.Brevity.Resource)
        .apply {
          for (function in abiResource.functions) {
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
  private fun generateVariant(irVariant: IrVariant) {
    val className = kotlinMapper.getAbiClassName(irVariant.type)
    collector.addType(
      className,
      TypeSpec.interfaceBuilder(className)
        .addModifiers(KModifier.SEALED)
        .setDeclaration(irVariant)
        .apply {
          for (case in irVariant.cases) {
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
  private fun generateEnum(irEnum: IrEnum) {
    val className = kotlinMapper.getAbiClassName(irEnum.type)
    collector.addType(
      className = className,
      type = TypeSpec.enumBuilder(className)
        .setDeclaration(irEnum)
        .apply {
          for (case in irEnum.cases) {
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
  private fun generateTypeAlias(irTypeAlias: IrTypeAlias) {
    val className = kotlinMapper.getAbiClassName(irTypeAlias.type)
    collector.addType(
      className = className,
      type = TypeSpec.classBuilder(className)
        .addModifiers(KModifier.VALUE)
        .addAnnotation(JvmInline::class)
        .setDeclaration(irTypeAlias)
        .apply {
          val targetType = kotlinMapper.get(irTypeAlias.target)
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
  private fun generateFlags(irFlags: IrFlags) {
    val className = kotlinMapper.getAbiClassName(irFlags.type)
    collector.addType(
      className = className,
      type = TypeSpec.classBuilder(className)
        .addModifiers(KModifier.DATA)
        .setDeclaration(irFlags)
        .apply {
          val constructorBuilder = FunSpec.constructorBuilder()

          for (field in irFlags.flags) {
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
  private fun generateServiceInterface(abiService: AbiService) {
    val typeName = abiService.apiClassName
    val typeSpecBuilder = when {
      !abiService.hasInstanceMembers || abiService is AbiWorld -> TypeSpec.objectBuilder(typeName)
      else -> TypeSpec.interfaceBuilder(typeName)
    }

    abiService.documentation?.let {
      typeSpecBuilder.addKdoc(it.content.trimIndent())
    }

    for (type in abiService.types) {
      when (type) {
        is IrEnum -> generateEnum(type)
        is IrFlags -> generateFlags(type)
        is IrRecord -> generateRecord(type)
        is IrResource -> {} // Handled by generateResource().
        is IrTypeAlias -> generateTypeAlias(type)
        is IrVariant -> generateVariant(type)
      }
    }

    if (abiService is AbiInterface) {
      for (function in abiService.functions) {
        typeSpecBuilder.addFunction(apiFunctionFactory.create(function))
      }
    }

    collector.addType(typeName, typeSpecBuilder.build())
  }

  context(collector: QualifiedSpecCollector)
  private fun generateWorldInterface(abiWorld: AbiWorld, orientation: Orientation) {
    val type = abiWorld.apiInterfaceName(orientation)
    collector.addType(
      className = type,
      type = TypeSpec.interfaceBuilder(type)
        .apply {
          for (abiInterface in abiWorld.interfaces) {
            if (abiInterface.orientation != orientation) continue
            addProperty(abiInterface.instanceName, abiInterface.apiClassName)
          }
          for (abiFunction in abiWorld.functions) {
            if (abiFunction.orientation != orientation) continue
            addFunction(apiFunctionFactory.create(abiFunction))
          }
        }
        .build(),
    )
  }

  private fun <T : Documentable.Builder<*>> T.setDeclaration(
    declaration: IrDeclaration? = null,
  ): T = apply {
    val documentation = declaration?.documentation
    if (documentation != null) {
      addKdoc(documentation.content.trimIndent())
    }
  }
}
