package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrParameter
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.MAX_FLAT_PARAMS

class CoreValueFactory(
  val encoderFactory: EncoderFactory,
  val nameAllocator: NameAllocator,
) {
  fun parameters(parameters: List<IrParameter>): ParameterListEncoder {
    val coreParameters = parameters.map {
      parameter(it.name, it.type)
    }

    if (coreParameters.sumOf { it.specs.size } <= MAX_FLAT_PARAMS) {
      return ParameterListEncoder.Flattened(coreParameters)
    }

    return ParameterListEncoder.Stored(
      addressSpec = ParameterSpec(
        nameAllocator.newName("parameterAddress"),
        CoreType.Pointer.kotlinCoreType,
      ),
      fieldEncoders = coreParameters.map { it.encoder },
    )
  }

  fun parameter(name: Identifier, typeName: TypeName): CoreParameter {
    val encoder = encoderFactory.get(typeName)
    val nameHints = encoder.nameHints

    val specs = mutableListOf<ParameterSpec>()
    val names = mutableListOf<String>()
    for ((v, coreType) in encoder.coreTypes.withIndex()) {
      val nameHint = nameHints?.getOrNull(v)
      val coreName = when {
        nameHint != null -> nameAllocator.newName(
          Identifier("${name}-${nameHint.name}").lowerCamelCase,
        )
        v == 0 -> nameAllocator[name]
        else -> nameAllocator.newName(
          suggestion = "${name.lowerCamelCase}${v + 1}",
          tag = name to v,
        )
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
