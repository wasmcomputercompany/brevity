package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory

class CoreValueFactory(
  val encoderFactory: EncoderFactory,
  val nameAllocator: NameAllocator,
) {
  fun parameter(name: Identifier, typeName: TypeName): CoreParameter {
    val encoder = encoderFactory.get(typeName)
    val nameHints = encoder.nameHints

    val specs = mutableListOf<ParameterSpec>()
    val names = mutableListOf<String>()
    for ((index, coreType) in encoder.coreTypes.withIndex()) {
      val nameHint = nameHints?.getOrNull(index)
      val coreName = when {
        nameHint != null -> nameAllocator.newName(
          Identifier("${name}-${nameHint.name}").toCamelCase(upperCamel = false),
        )

        else -> nameAllocator[name]
      }
      specs += ParameterSpec(coreName, coreType.kotlinCoreType)
      names += coreName
    }

    return CoreParameter(
      encoder = encoder,
      specs = specs,
      names = names,
    )
  }

  fun result(type: TypeName): CoreResult {
    val encoder = encoderFactory.get(type)
    return CoreResult(
      name = nameAllocator.newName("result"),
      type = type,
      encoder = encoder,
      parameter = when {
        encoder.coreTypes.size > 1 -> {
          ParameterSpec(
            nameAllocator.newName("resultParameter"),
            CoreType.Pointer.kotlinCoreType,
          )
        }

        else -> null
      },
    )
  }
}
