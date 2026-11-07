package dev.wasmo.brevity.kotlin.abi

import dev.wasmo.brevity.ir.IrStream

data class AbiStream(
  val irStream: IrStream,
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
}
