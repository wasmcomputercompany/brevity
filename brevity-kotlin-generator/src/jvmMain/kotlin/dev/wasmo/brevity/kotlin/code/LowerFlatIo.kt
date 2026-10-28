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
  /**
   * If calling [put] conditionally through different code paths (if/else), use this to mark a
   * position to reset back to. If the code builder is using local variables, this call makes sure
   * those local variables are declared.
   */
  context(codeBuilder: CodeBuilder)
  fun mark(count: Int): Int

  fun reset(tag: Int)

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
  return LocalVariablesLowerFlatIo(
    parameters.flatMap { parameter ->
      parameter.loweredSpecs.zip(parameter.encoder.coreTypes).map { it.first.name to it.second }
    },
  )
}

context(codeBuilder: CodeBuilder)
fun localVariablesLowerFlatIo(name: String, coreType: CoreType): LowerFlatIo =
  LocalVariablesLowerFlatIo(listOf(name to coreType))

/** Lower a value into a `CallBuilder`. */
class CallBuilderLowerFlatIo(
  val name: String,
  val coreTypes: List<CoreType>,
) : LowerFlatIo {
  val type: TypeName
    get() = Symbols.Brevity.CallBuilder

  private var putCount = 0
  private var takeCount = 0

  context(codeBuilder: CodeBuilder)
  override fun mark(count: Int) = putCount

  override fun reset(tag: Int) {
    putCount = tag
  }

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

/**
 * Lower a value into one or more local variables.
 *
 * This declares local variables only if [mark] is called; otherwise it assumes each local variable
 * can be inlined into the [take] call.
 */
private class LocalVariablesLowerFlatIo(
  variables: List<Pair<String, CoreType>>,
) : LowerFlatIo {
  private var vars = MutableList<Var>(variables.size) { Var.Unassigned(variables[it]) }
  private var putCount = 0
  private var takeCount = 0

  context(codeBuilder: CodeBuilder)
  override fun mark(count: Int): Int {
    for (i in putCount until putCount + count) {
      val v = vars[i]
      val initialValue = when (v) {
        is Var.Unassigned -> v.coreType.kotlinCoreType.placeholder
        is Var.Expression -> v.value
        is Var.Local -> continue
      }

      val valueName = codeBuilder.newName(v.name)
      codeBuilder.addStatement("var %N = %L", valueName, initialValue)
      vars[i] = Var.Local(valueName)
    }
    return putCount
  }

  override fun reset(tag: Int) {
    putCount = tag
  }

  context(codeBuilder: CodeBuilder)
  override fun put(value: CodeBlock) {
    when (val v = vars[putCount]) {
      is Var.Expression -> error("multiple conflicting assignment to var")
      is Var.Local -> {
        codeBuilder.addStatement("%N = %L", v.name, value)
      }
      is Var.Unassigned -> {
        vars[putCount] = Var.Expression(v.name, value)
      }
    }
    putCount++
  }

  context(codeBuilder: CodeBuilder)
  override fun skip(count: Int) {
    putCount += count
  }

  context(codeBuilder: CodeBuilder)
  override fun take() = vars[takeCount++].value

  sealed interface Var {
    val name: String
    val value: CodeBlock

    class Unassigned(variable: Pair<String, CoreType>) : Var {
      override val name = variable.first
      val coreType = variable.second
      override val value: CodeBlock
        get() = error("unassigned: $name")
    }

    class Local(
      override val name: String,
    ) : Var {
      override val value: CodeBlock
        get() = CodeBlock.of("%N", name)
    }

    class Expression(
      override val name: String,
      override val value: CodeBlock,
    ) : Var
  }
}
