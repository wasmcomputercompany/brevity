package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.TypeName as WitTypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.encoders.AbstractRecordEncoder
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.MAX_FLAT_PARAMS
import dev.wasmo.brevity.kotlin.generator.isSupported
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType
import dev.wasmo.brevity.kotlin.generator.kotlinIdentifier
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase

/**
 * Describes a function from the perspective of the ABI.
 *
 * This function knows if it's imported or exported, and can compute call structure based on that.
 */
data class AbiFunction(
  val name: FunctionName,
  val kotlinIdentifier: Identifier,
  val documentation: String? = null,
  val parameters: Parameters,
  val result: Result = Result.Void,
  val async: Boolean = false,
  val isSupported: Boolean,
  val orientation: Orientation,
  /** True if this function has an implicit `id` parameter. */
  val isResource: Boolean,
  private val nameAllocator: NameAllocator,
) {
  val kotlinName: String
    get() = kotlinIdentifier.lowerCamelCase

  fun newNameAllocator() = nameAllocator.copy()

  val loweredParameterSpecs: List<ParameterSpec>
    get() = buildList {
      if (isResource) {
        add(ParameterSpec.builder("id", INT).build())
      }

      when (parameters) {
        is Parameters.Flattened -> {
          for (coreParameter in parameters.parameters) {
            addAll(coreParameter.loweredSpecs)
          }
        }

        is Parameters.Stored -> {
          add(parameters.addressSpec)
        }
      }

      if (result is Result.PointerParameter) {
        add(result.parameter)
      }
    }

  val loweredParameterTypes: List<CoreType>
    get() = buildList {
      if (isResource) {
        add(CoreType.I32)
      }

      when (parameters) {
        is Parameters.Flattened -> {
          for (coreParameter in parameters.parameters) {
            addAll(coreParameter.encoder.coreTypes)
          }
        }

        is Parameters.Stored -> {
          add(CoreType.Pointer)
        }
      }

      if (result is Result.PointerParameter) {
        add(CoreType.Pointer)
      }
    }

  sealed interface Parameters {
    val lowerAllocates: Boolean
    val liftedSpecs: List<ParameterSpec>

    class Flattened(
      val parameters: List<FlatParameter>,
    ) : Parameters {
      override val lowerAllocates: Boolean
        get() = parameters.any { it.encoder.lowerAllocates }
      override val liftedSpecs: List<ParameterSpec>
        get() = parameters.map { it.liftedSpec }
    }

    class FlatParameter(
      val encoder: Encoder,
      val liftedSpec: ParameterSpec,
      val loweredSpecs: List<ParameterSpec>,
    )

    class Stored(
      override val liftedSpecs: List<ParameterSpec>,
      val addressSpec: ParameterSpec,
      fieldEncoders: List<Encoder>,
    ) : AbstractRecordEncoder(fieldEncoders), Parameters {
      override val instanceNameHint: String
        get() = "parameters"

      override fun fieldValuesToInstance(fieldValues: List<CodeBlock>) =
        error("no instance for a list of parameters")

      override fun instanceToFieldValues(record: CodeBlock) =
        error("no instance for a list of parameters")

      override val lowerAllocates: Boolean
        get() = true
    }
  }

  sealed interface Result {
    val value: Value?
    val loweredType: CoreType?

    /** The function does not return a value. */
    object Void : Result {
      override val value: Value?
        get() = null
      override val loweredType: CoreType?
        get() = null
    }

    /** Encode the result as a single core value and return it. */
    data class SingleCoreValueReturn(
      override val value: Value,
    ) : Result {
      override val loweredType: CoreType
        get() = value.encoder.coreTypes.single()
    }

    /** The callee allocates memory, writes the result there, and returns the address. */
    data class PointerReturn(
      override val value: Value,
    ) : Result {
      override val loweredType: CoreType
        get() = CoreType.Pointer
    }

    /** The caller allocates memory, and passes the address as a parameter. */
    data class PointerParameter(
      override val value: Value,
      val parameter: ParameterSpec,
    ) : Result {
      override val loweredType: CoreType?
        get() = null
    }

    data class Value(
      val encoder: Encoder,
      val type: TypeName,
    )
  }

  class Factory(
    private val kotlinMapper: KotlinMapper,
    private val encoderFactory: EncoderFactory,
  ) {
    fun createAll(
      functions: List<IrFunction>,
      orientation: Orientation,
      isResource: Boolean = false,
    ): List<AbiFunction> = functions.map { function -> create(function, orientation, isResource) }

    fun create(
      irFunction: IrFunction,
      orientation: Orientation,
      isResource: Boolean,
    ): AbiFunction {
      val nameAllocator = NameAllocator()
      val flatParameters = irFunction.parameters.map { parameter ->
        flatParameter(parameter.type, parameter.name, nameAllocator)
      }

      return AbiFunction(
        name = irFunction.functionName,
        kotlinIdentifier = irFunction.functionName.kotlinIdentifier,
        documentation = documentation(irFunction, flatParameters),
        parameters = parameters(flatParameters, nameAllocator),
        result = result(nameAllocator, irFunction, orientation),
        async = irFunction.async,
        isSupported = irFunction.isSupported,
        orientation = orientation,
        isResource = isResource,
        nameAllocator = nameAllocator,
      )
    }

    fun asyncCallback(
      irFunction: IrFunction,
      orientation: Orientation,
      isResource: Boolean,
    ): AbiFunction {
      val nameAllocator = NameAllocator()
      val flatParameters = listOf(
        flatParameter(WitTypeName.U32, Identifier("eventCode"), nameAllocator),
        flatParameter(WitTypeName.U32, Identifier("p1"), nameAllocator),
        flatParameter(WitTypeName.U32, Identifier("p2"), nameAllocator),
      )

      return AbiFunction(
        name = FunctionName.AsyncLiftCallback(irFunction.functionName),
        kotlinIdentifier = irFunction.functionName.kotlinIdentifier,
        parameters = parameters(flatParameters, nameAllocator),
        nameAllocator = nameAllocator,
        orientation = orientation,
        isResource = isResource,
        isSupported = irFunction.isSupported,
      )
    }

    fun taskReturn(
      irFunction: IrFunction,
      orientation: Orientation,
      isResource: Boolean,
    ): AbiFunction {
      val nameAllocator = NameAllocator()
      val flatParameters = buildList {
        val returnType = irFunction.returnType
        if (returnType != null) {
          add(
            flatParameter(
              type = returnType,
              name = Identifier("lifted-result"),
              nameAllocator = nameAllocator,
            )
          )
        }
      }

      return AbiFunction(
        name = FunctionName.TaskReturn(irFunction.functionName),
        kotlinIdentifier = irFunction.functionName.kotlinIdentifier,
        parameters = parameters(flatParameters, nameAllocator),
        nameAllocator = nameAllocator,
        isSupported = irFunction.isSupported,
        orientation = orientation,
        isResource = isResource,
      )
    }

    private fun flatParameter(
      type: WitTypeName,
      name: Identifier,
      nameAllocator: NameAllocator,
    ): Parameters.FlatParameter {
      val encoder = encoderFactory.get(type)
      val spec = ParameterSpec.builder(
        nameAllocator.newName(name.lowerCamelCase),
        kotlinMapper.get(type),
      ).build()
      return Parameters.FlatParameter(
        encoder = encoder,
        liftedSpec = spec,
        loweredSpecs = encoder.coreTypes.withIndex().map { (v, coreType) ->
          val nameHint = encoder.nameHints?.getOrNull(v)
          val coreName = when {
            nameHint != null && encoder.coreTypes.size > 1 ->
              nameAllocator.newName(Identifier("$name-${nameHint.name}").lowerCamelCase)

            v == 0 -> spec.name
            else -> nameAllocator.newName(Identifier("$name${v + 1}").lowerCamelCase)
          }
          ParameterSpec.builder(
            coreName,
            coreType.kotlinCoreType,
          ).build()
        },
      )
    }

    private fun parameters(
      flatParameters: List<Parameters.FlatParameter>,
      nameAllocator: NameAllocator,
    ): Parameters {
      return when {
        flatParameters.sumOf { it.loweredSpecs.size } <= MAX_FLAT_PARAMS ->
          Parameters.Flattened(flatParameters)

        else -> Parameters.Stored(
          liftedSpecs = flatParameters.map { it.liftedSpec },
          addressSpec = ParameterSpec(
            nameAllocator.newName("parameterAddress"),
            CoreType.Pointer.kotlinCoreType,
          ),
          fieldEncoders = flatParameters.map { it.encoder },
        )
      }
    }

    private fun result(
      nameAllocator: NameAllocator,
      irFunction: IrFunction,
      orientation: Orientation,
    ): Result {
      val returnType = irFunction.returnType ?: return Result.Void

      val value = Result.Value(
        encoder = encoderFactory.get(returnType),
        type = kotlinMapper.get(returnType),
      )

      return when {
        value.encoder.coreTypes.size == 1 -> Result.SingleCoreValueReturn(value)
        orientation == Orientation.Import -> Result.PointerParameter(
          value,
          ParameterSpec(
            nameAllocator.newName("resultParameter"),
            CoreType.Pointer.kotlinCoreType,
          ),
        )

        else -> Result.PointerReturn(value)
      }
    }

    private fun documentation(
      value: IrFunction,
      parameters: List<Parameters.FlatParameter>,
    ): String? {
      val result = buildString {
        val functionDocumentation = value.documentation
        if (functionDocumentation != null) {
          append(functionDocumentation.content.trimIndent())
          append("\n\n")
        }

        for ((index, parameter) in value.parameters.withIndex()) {
          val parameterDocumentation = parameter.documentation ?: continue
          append("@param ${parameters[index].liftedSpec.name} ")
          append(parameterDocumentation.content.trimIndent().replace("\n", "\n  "))
          append("\n\n")
        }
      }

      return result.trim().takeIf { it.isNotEmpty() }
    }
  }
}
