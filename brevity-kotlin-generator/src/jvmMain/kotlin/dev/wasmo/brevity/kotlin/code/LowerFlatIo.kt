package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import dev.wasmo.brevity.kotlin.abi.AbiFunction.FlatParameter
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.expressions.placeholder
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

/**
 * Accepts 1 or more lowered values for a lower flat operation.
 *
 * Declared types that are flattened into multiple core types have `liftFlat()` functions that
 * accept a `CallBuilder` and return no result. Declared types that are flattened into a single type
 * return that value instead.
 */
sealed interface LowerFlatIo {
  var putCount: Int

  context(codeBuilder: CodeBuilder)
  fun put(value: CodeBlock)

  context(codeBuilder: CodeBuilder)
  fun skip(count: Int)

  context(codeBuilder: CodeBuilder)
  fun take(): CodeBlock
}

context(codeBuilder: CodeBuilder)
fun callBuilderLowerFlatIo(parameters: List<FlatParameter>): CallBuilderLowerFlatIo {
  val coreTypes = parameters.flatMap { it.encoder.coreTypes }
  val result = CallBuilderLowerFlatIo(
    name = codeBuilder.newName("callBuilder"),
    coreTypes = coreTypes,
  )
  codeBuilder.addStatement("val %N = %T(%L)", result.name, result.type, coreTypes.size)
  return result
}

@JvmName("localVariablesLowerFlatIo2")
context(codeBuilder: CodeBuilder)
fun localVariablesLowerFlatIo(parameters: List<FlatParameter>): LowerFlatIo {
  return localVariablesLowerFlatIo(
    vars = parameters.flatMap { parameter ->
      parameter.loweredSpecs.zip(parameter.encoder.coreTypes).map { it.first.name to it.second }
    },
  )
}

context(codeBuilder: CodeBuilder)
fun localVariablesLowerFlatIo(vars: List<Pair<String, CoreType>>): LowerFlatIo {
  val coreTypes = mutableListOf<CoreType>()
  val valueNames = mutableListOf<String>()
  for ((name, coreType) in vars) {
    val valueName = codeBuilder.newName(name)
    codeBuilder.addStatement("var %N = %L", valueName, coreType.kotlinCoreType.placeholder)
    valueNames += valueName
    coreTypes += coreType
  }
  return LocalVariablesLowerFlatIo(
    valueNames = valueNames,
  )
}

/** Lower a value into a `CallBuilder`. */
class CallBuilderLowerFlatIo(
  val name: String,
  val coreTypes: List<CoreType>,
) : LowerFlatIo {
  val type: TypeName
    get() = Symbols.Brevity.CallBuilder

  override var putCount = 0
  private var takeCount = 0

  context(codeBuilder: CodeBuilder)
  override fun put(value: CodeBlock) {
    putCount++
    codeBuilder.addStatement("%N.put(%L)", name, value)
  }

  context(codeBuilder: CodeBuilder)
  override fun skip(count: Int) {
    if (count == 0) return
    putCount += count
    codeBuilder.addStatement("%N.skip(%L)", name, count)
  }

  context(codeBuilder: CodeBuilder)
  override fun take(): CodeBlock {
    val type = coreTypes[takeCount++]
    return when (type) {
      CoreType.I32, CoreType.Pointer -> CodeBlock.of("%N.takeI32()", name)
      CoreType.I64 -> CodeBlock.of("%N.takeI64()", name)
      CoreType.F32 -> CodeBlock.of("%N.takeF32()", name)
      CoreType.F64 -> CodeBlock.of("%N.takeF64()", name)
    }
  }
}

/** Lower a value into one or more local variables. */
private class LocalVariablesLowerFlatIo(
  private val valueNames: List<String>,
) : LowerFlatIo {
  override var putCount = 0
  private var takeCount = 0

  context(codeBuilder: CodeBuilder)
  override fun put(value: CodeBlock) {
    codeBuilder.addStatement("%N = %L", valueNames[putCount++], value)
  }

  context(codeBuilder: CodeBuilder)
  override fun skip(count: Int) {
  }

  context(codeBuilder: CodeBuilder)
  override fun take() = CodeBlock.of("%N", valueNames[takeCount++])
}
