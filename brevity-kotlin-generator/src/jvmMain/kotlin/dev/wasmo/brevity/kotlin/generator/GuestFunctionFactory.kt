package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncodeBuilder
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.byteCount
import dev.wasmo.brevity.kotlin.encoders.wasmExportAnnotation
import dev.wasmo.brevity.kotlin.encoders.wasmImportAnnotation
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Creates bridge functions that run on the guest.
 */
internal class GuestFunctionFactory(
  private val encoderFactory: EncoderFactory,
  private val receiver: Receiver,
  private val value: IrFunction,
) {
  private val used = AtomicBoolean()
  private val encodeBuilder = GuestEncodeBuilder()

  private val nameAllocator = NameAllocator().apply {
    // Pre-allocate the names we'll need.
    for (parameter in value.parameters) {
      newName(parameter.kotlinName, parameter.name)
    }
    if (receiver is Receiver.Id) {
      newName(receiver.name.toCamelCase(upperCamel = false), receiver.name)
    }
  }

  private val code = CodeBlock.Builder()
  private var memoryAllocator: String? = null

  private val coreReceiver: CoreParameter? = when {
    receiver is Receiver.Id -> coreParameter(receiver.name, receiver.type)
    else -> null
  }
  private val coreParameters = value.parameters.map { coreParameter(it.name, it.type) }

  /** Bridge an API function into a call to [wasmImport]. */
  fun callHost(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    return FunSpec.builder(value.kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        val loweredParameters = mutableListOf<CodeBlock>()
        loweredParameters += CodeBlock.of("this.%L", "id")

        for ((index, parameter) in value.parameters.withIndex()) {
          addParameter(nameAllocator[parameter.name], parameter.type.kotlinApi)
          loweredParameters += encodeBuilder.lower(
            value = CodeBlock.of("%N", nameAllocator[parameter.name]),
            encoder = coreParameters[index].encoder,
          )
        }

        val result = nameAllocator.newName("result")
        val returnType = value.returnType
        if (returnType != null) {
          code.add("val %N = ", result)
        }
        code.add("%N(⇥\n", value.functionName.importFunctionName)
        for (output in loweredParameters) {
          code.add("%L,\n", output)
        }
        code.add("⇤)\n")

        if (returnType != null) {
          returns(returnType.kotlinApi)
          val encoder = encoderFactory.get(typeName = returnType)
          val coreReturnValues = when (encoder.coreTypes.size) {
            1 -> listOf(CodeBlock.of("%N", result))
            else -> unflattenResult(CodeBlock.of("%N", result), encoder)
          }
          val liftedReturnValue = encodeBuilder.lift(
            values = coreReturnValues,
            encoder = encoder,
          )
          code.add("return %L", liftedReturnValue)
        }
      }
      .addCode(buildCodeBlock())
      .build()
  }

  /** Returns the `@WasmImport`-annotated function. It must be added directly to a file. */
  fun wasmImport(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    return FunSpec.builder(value.functionName.importFunctionName)
      .addAnnotation(value.functionName.wasmImportAnnotation)
      .addModifiers(KModifier.PRIVATE, KModifier.EXTERNAL)
      .apply {
        if (coreReceiver != null) {
          addParameters(coreReceiver.specs)
        }
        for (coreParameter in coreParameters) {
          addParameters(coreParameter.specs)
        }

        val returnType = value.returnType
        if (returnType != null) {
          val encoder = encoderFactory.get(returnType)
          when (encoder.coreTypes.size) {
            1 -> returns(encoder.coreTypes.single().kotlinCoreType)
            else -> returns(CoreType.Pointer.kotlinCoreType)
          }
        }
      }
      .build()
  }

  /** Returns the `@WasmExport`-annotated function. It must be added directly to a file. */
  fun wasmExport(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    return FunSpec.builder(value.functionName.exportFunctionName)
      .addAnnotation(value.functionName.wasmExportAnnotation)
      .addModifiers(KModifier.PRIVATE)
      .apply {
        val liftedReceiver = when (receiver) {
          is Receiver.Id -> {
            addParameters(coreReceiver!!.specs)
            encodeBuilder.lift(coreReceiver.values, coreReceiver.encoder)
          }

          is Receiver.Global -> receiver.codeBlock
        }

        val liftedParameterValues = mutableListOf<CodeBlock>()
        for (coreParameter in coreParameters) {
          addParameters(coreParameter.specs)
          liftedParameterValues += encodeBuilder.lift(
            values = coreParameter.values,
            encoder = coreParameter.encoder,
          )
        }

        val result = nameAllocator.newName("result")
        val returnType = value.returnType
        if (returnType != null) {
          code.add("val %N = ", result)
        }
        code.add("%L.%N(⇥\n", liftedReceiver, value.kotlinName)
        for ((index, parameter) in value.parameters.withIndex()) {
          code.add("%N = %L,\n", nameAllocator[parameter.name], liftedParameterValues[index])
        }
        code.add("⇤)\n")

        if (returnType != null) {
          val encoder = encoderFactory.get(typeName = returnType)
          val loweredReturnValues = encodeBuilder.lower(
            value = CodeBlock.of("%N", result),
            encoder = encoder,
          )
          when (encoder.coreTypes.size) {
            1 -> {
              returns(encoder.coreTypes.single().kotlinCoreType)
              code.add("return %L\n", loweredReturnValues.single())
            }

            else -> {
              returns(CoreType.Pointer.kotlinCoreType)
              code.add("return %L\n", flattenResult(loweredReturnValues, encoder))
            }
          }
        }
      }
      .addCode(buildCodeBlock())
      .build()
  }

  /** When there's multiple core values to return, write them to memory and return a pointer. */
  private fun flattenResult(returnValues: List<CodeBlock>, encoder: Encoder): CodeBlock {
    val coreTypes = encoder.coreTypes

    val byteCount = coreTypes.sumOf { coreType ->
      coreType.byteCount
    }

    val pointer = nameAllocator.newName("resultPointer")
    var offset = 0
    code.addStatement("val %N = %L", pointer, encodeBuilder.allocate("%L", byteCount))
    for ((index, value) in returnValues.withIndex()) {
      val coreType = coreTypes[index]
      val offsetPointer = when {
        offset == 0 -> CodeBlock.of("%N", pointer)
        else -> CodeBlock.of("(%N + %L)", pointer, offset)
      }
      when (coreType) {
        CoreType.I64, CoreType.F64 -> code.addStatement("%L.storeLong(%L)", offsetPointer, value)
        else -> code.addStatement("%L.storeInt(%L)", offsetPointer, value)
      }
      offset += coreType.byteCount
    }

    return CodeBlock.of("%N.address.toInt()", pointer)
  }

  /** When a pointer is returned, unpack the core values from memory. */
  private fun unflattenResult(returnValue: CodeBlock, encoder: Encoder): List<CodeBlock> {
    val coreTypes = encoder.coreTypes
    val nameHints = encoder.nameHints

    val pointer = nameAllocator.newName("resultPointer")
    code.addStatement("val %N = %T(%L.toUInt())", pointer, Symbols.KotlinWasm.Pointer, returnValue)

    var offset = 0
    val result = mutableListOf<CodeBlock>()

    for ((index, coreType) in coreTypes.withIndex()) {
      val nameHint = nameHints?.getOrNull(index)
      val nameSuggestion = when {
        nameHint != null -> Identifier("result-${nameHint}").toCamelCase(upperCamel = false)
        else -> "result"
      }
      val name = nameAllocator.newName(nameSuggestion)
      result += CodeBlock.of("%N", name)
      code.addStatement("val %N = (%N + %L).loadInt()", name, pointer, offset)
      offset += coreType.byteCount
    }

    return result
  }

  private fun buildCodeBlock(): CodeBlock {
    return when {
      memoryAllocator != null -> com.squareup.kotlinpoet.buildCodeBlock {
        beginControlFlow(
          "%M { %N ->",
          Symbols.KotlinWasm.WithScopedMemoryAllocator,
          memoryAllocator,
        )
        add(code.build())
        endControlFlow()
      }

      else -> code.build()
    }
  }

  private fun coreParameter(name: Identifier, typeName: TypeName): CoreParameter {
    val encoder = encoderFactory.get(typeName)
    val nameHints = encoder.nameHints

    val specs = mutableListOf<ParameterSpec>()
    val values = mutableListOf<CodeBlock>()
    for ((index, coreType) in encoder.coreTypes.withIndex()) {
      val nameHint = nameHints?.getOrNull(index)
      val coreName = when {
        nameHint != null -> nameAllocator.newName(
          Identifier("${name}-${nameHint.name}").toCamelCase(upperCamel = false),
        )

        else -> nameAllocator[name]
      }
      specs += ParameterSpec(coreName, coreType.kotlinCoreType)
      values += CodeBlock.of("%N", coreName)
    }

    return CoreParameter(
      encoder = encoder,
      specs = specs,
      values = values,
    )
  }

  /**
   * A set of parameters that use core types only, to be lifted on inbound calls and lowered on
   * outbound calls.
   */
  private class CoreParameter(
    val encoder: Encoder,
    val specs: List<ParameterSpec>,
    val values: List<CodeBlock>,
  )

  private inner class GuestEncodeBuilder : EncodeBuilder {
    override val bridge: CodeBlock
      get() = CodeBlock.of("%T", Symbols.Brevity.GuestBridge)
    override val nameAllocator: NameAllocator
      get() = this@GuestFunctionFactory.nameAllocator
    override val code: CodeBlock.Builder
      get() = this@GuestFunctionFactory.code

    private val inputs = mutableListOf<CodeBlock>()
    private val outputs = mutableListOf<CodeBlock>()

    override fun allocate(byteCount: CodeBlock): CodeBlock {
      if (memoryAllocator == null) {
        memoryAllocator = nameAllocator.newName("memoryAllocator")
      }
      return CodeBlock.of("%N.allocate(%L)", memoryAllocator, byteCount)
    }

    override fun take(): CodeBlock {
      return inputs.removeFirstOrNull()
        ?: error("unexpected call to take(), input count mismatch?")
    }

    override fun put(value: CodeBlock) {
      outputs += value
    }

    fun lower(value: CodeBlock, encoder: Encoder): List<CodeBlock> {
      inputs += value

      with(encoder) {
        valueToCoreType()
      }

      check(inputs.isEmpty()) {
        "expected 1 call to take(), but was 0"
      }

      check(outputs.size == encoder.coreTypes.size) {
        "expected ${encoder.coreTypes.size} calls to put(), but was ${outputs.size}"
      }

      return outputs.toList()
        .also { outputs.clear() }
    }

    fun lift(values: List<CodeBlock>, encoder: Encoder): CodeBlock {
      inputs += values

      with(encoder) {
        coreTypeToValue()
      }

      check(inputs.isEmpty()) {
        "expected ${values.size} calls to take(), but was ${values.size - inputs.size}"
      }

      check(outputs.size == 1) {
        "expected 1 call to put(), but was ${outputs.size}"
      }

      return outputs.removeFirst()
    }
  }

  internal sealed interface Receiver {
    data class Global(
      val codeBlock: CodeBlock,
    ) : Receiver

    data class Id(
      val type: TypeName,
    ) : Receiver {
      val name: Identifier
        get() = Identifier("self")
    }
  }
}
