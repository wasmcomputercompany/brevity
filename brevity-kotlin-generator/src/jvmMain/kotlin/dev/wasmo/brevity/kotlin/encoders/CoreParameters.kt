package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrParameter
import dev.wasmo.brevity.kotlin.generator.GuestBridgeBuilder
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType
import dev.wasmo.brevity.kotlin.generator.kotlinName

class CoreParameters(
  val encoder: Encoder,
  val specs: List<ParameterSpec>,
  val values: List<CodeBlock>,
)

internal fun EncoderFactory.coreParameters(
  nameAllocator: NameAllocator,
  receiver: GuestBridgeBuilder.Receiver.Id,
) = coreParameters(nameAllocator, receiver.name, receiver.type)

internal fun EncoderFactory.coreParameters(
  nameAllocator: NameAllocator,
  parameter: IrParameter,
) = coreParameters(nameAllocator, parameter.name, parameter.type)

internal fun EncoderFactory.coreParameters(
  nameAllocator: NameAllocator,
  name: Identifier,
  typeName: TypeName,
): CoreParameters {
  val encoder = get(typeName)
  val nameHints = encoder.nameHints

  val specs = mutableListOf<ParameterSpec>()
  val values = mutableListOf<CodeBlock>()
  for ((index, coreType) in encoder.coreTypes.withIndex()) {
    val coreName = nameAllocator.newName(name.kotlinName(nameHints?.getOrNull(index)))
    specs += ParameterSpec(coreName, coreType.kotlinCoreType)
    values += CodeBlock.of("%N", coreName)
  }

  return CoreParameters(
    encoder = encoder,
    specs = specs,
    values = values,
  )
}
