package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.TypeName as TypeNameKt
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrStream
import dev.wasmo.brevity.kotlin.encoders.Encoder

data class AbiStream(
  val irStream: IrStream,
  val elementEncoder: Encoder?,
  val elementType: TypeNameKt,
  val new: AbiFunction,
  val ioRead: AbiFunction,
  val cancelRead: AbiFunction,
  val dropRead: AbiFunction,
  val ioWrite: AbiFunction,
  val cancelWrite: AbiFunction,
  val dropWrite: AbiFunction,
) {
  val functions: List<AbiFunction>
    get() = listOf(
      new,
      ioRead,
      cancelRead,
      dropRead,
      ioWrite,
      cancelWrite,
      dropWrite,
    )

  val cardinality: TypeName.Stream.Cardinality
    get() = irStream.type.cardinality
}
