package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.Orientation.Import
import dev.wasmo.brevity.TypeName as WitTypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.encoders.AbstractRecordEncoder
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.IntEncoder
import dev.wasmo.brevity.kotlin.generator.isSupported
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType
import dev.wasmo.brevity.kotlin.generator.kotlinIdentifier
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase

/**
 * Describes a function from the perspective of the ABI.
 *
 * This function knows if it's imported or exported, and can compute call structure based on that.
 *
 * Each [IrFunction] corresponds to one or more instances of this class:
 *
 *  * Synchronous calls are mapped to a single instance.
 *
 *  * Exported async calls are mapped to two instances in the same orientation:
 *    [FunctionName.AsyncExport] and [FunctionName.AsyncExportCallback], and one instance in the
 *    mirror orientation, [FunctionName.TaskReturn].
 *
 *  * Imported async calls are mapped to an instance of [FunctionName.AsyncImport].
 */
data class AbiFunction(
  val name: FunctionName,
  val kotlinIdentifier: Identifier,
  val documentation: String? = null,
  val parameters: Parameters,
  val result: Result = Result.Void,
  val isSupported: Boolean,
  val orientation: Orientation,
  private val nameAllocator: NameAllocator,
) {
  val kotlinName: String
    get() = kotlinIdentifier.lowerCamelCase

  val async: Boolean
    get() = name is FunctionName.AsyncExport || name is FunctionName.AsyncImport

  /** Returns true if this function is not exposed to the application layer. */
  val isSynthetic: Boolean
    get() = name is FunctionName.TaskReturn ||
      name is FunctionName.AsyncExportCallback ||
      name is FunctionName.ResourceDrop ||
      name is FunctionName.ResourceNew

  val isResource: Boolean
    get() = parameters.resourceRep != null

  fun newNameAllocator() = nameAllocator.copy()

  val loweredParameterSpecs: List<ParameterSpec>
    get() = buildList {
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

  sealed interface FlatParameter {
    val name: String
    val type: TypeName
    val encoder: Encoder
    val loweredSpecs: List<ParameterSpec>

    /** A regular parameter. */
    data class Value(
      override val name: String,
      override val type: TypeName,
      override val encoder: Encoder,
      override val loweredSpecs: List<ParameterSpec>,
    ) : FlatParameter

    /** The resource rep is a parameter when lowered, and the receiver when lifted. */
    data class ResourceRep(
      override val name: String,
    ) : FlatParameter {
      override val type: TypeName
        get() = INT
      override val encoder: Encoder
        get() = IntEncoder
      override val loweredSpecs: List<ParameterSpec>
        get() = listOf(ParameterSpec.builder(name, type).build())
    }
  }

  sealed interface Parameters {
    val parameters: List<FlatParameter>
    val lowerAllocates: Boolean
    val liftedSpecs: List<ParameterSpec>
      get() = parameters.filterIsInstance<FlatParameter.Value>()
        .map { ParameterSpec.builder(it.name, it.type).build() }
    val resourceRep: FlatParameter.ResourceRep?
      get() = parameters.filterIsInstance<FlatParameter.ResourceRep>().singleOrNull()

    class Flattened(
      override val parameters: List<FlatParameter>,
    ) : Parameters {
      override val lowerAllocates: Boolean
        get() = parameters.any { it.encoder.lowerAllocates }
    }

    class Stored(
      override val parameters: List<FlatParameter>,
      val addressSpec: ParameterSpec,
    ) : AbstractRecordEncoder(parameters.map { it.encoder }), Parameters {
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

  /**
   * What the lowered function returns. For certain call encodings this is unrelated to what the
   * lifted function returns.
   */
  val loweredReturnType: CoreType?
    get() {
      if (name is FunctionName.AsyncImport) return CoreType.I32
      return when (result) {
        is Result.CallTaskReturn -> CoreType.I32
        is Result.PointerParameter -> null
        is Result.PointerReturn -> CoreType.Pointer
        is Result.SingleCoreValueReturn -> result.value.encoder.coreTypes.single()
        Result.Void -> null
      }
    }

  /** How the call result is transferred. */
  sealed interface Result {
    val value: Value?

    /** The function does not return a value. */
    object Void : Result {
      override val value: Value?
        get() = null
    }

    /** Encode the result as a single core value and return it. */
    data class SingleCoreValueReturn(
      override val value: Value,
    ) : Result

    /** The callee allocates memory, writes the result there, and returns the address. */
    data class PointerReturn(
      override val value: Value,
    ) : Result

    /** The caller allocates memory, and passes the address as a parameter. */
    data class PointerParameter(
      override val value: Value,
      val parameter: ParameterSpec,
    ) : Result

    /**
     * Call a callback function to provide the result to the caller. The lowered function returns a
     * `PackedAsyncResult`
     */
    data class CallTaskReturn(
      override val value: Value?,
      val taskReturn: AbiFunction,
    ) : Result

    data class Value(
      val encoder: Encoder,
      val type: TypeName,
    )
  }

  class Factory(
    private val kotlinMapper: KotlinMapper,
    private val encoderFactory: EncoderFactory,
  ) {
    fun collectAll(
      listBuilder: MutableList<AbiFunction>,
      functions: List<IrFunction>,
      orientation: Orientation,
      isResource: Boolean = false,
      includeSyntheticFunctions: Boolean = true,
    ) {
      for (irFunction in functions) {
        collectAll(
          listBuilder = listBuilder,
          irFunction = irFunction,
          orientation = orientation,
          isResource = isResource,
          includeSyntheticFunctions = includeSyntheticFunctions,
        )
      }
    }

    fun collectAll(
      listBuilder: MutableList<AbiFunction>,
      irFunction: IrFunction,
      orientation: Orientation,
      isResource: Boolean = false,
      includeSyntheticFunctions: Boolean = true,
    ) {
      when {
        irFunction.async && orientation == Export -> {
          val taskReturn = taskReturn(
            irFunction = irFunction,
            orientation = orientation.mirror,
          )
          listBuilder += create(
            irFunction = irFunction,
            orientation = orientation,
            isResource = isResource,
            taskReturn = taskReturn,
          )
          if (includeSyntheticFunctions) {
            listBuilder += asyncCallback(
              irFunction = irFunction,
              orientation = orientation,
              isResource = isResource,
            )
            listBuilder += taskReturn
          }
        }

        else -> listBuilder += create(irFunction, orientation, isResource)
      }
    }

    fun collectAllResourceFunctions(
      listBuilder: MutableList<AbiFunction>,
      types: List<IrTypeDeclaration>,
      orientation: Orientation,
    ) {
      for (type in types) {
        if (type !is IrResource) continue

        listBuilder += resourceDrop(orientation, type.type)
        if (orientation == Export) {
          listBuilder += resourceNew(orientation, type.type)
        }
      }
    }

    private fun create(
      irFunction: IrFunction,
      orientation: Orientation,
      isResource: Boolean,
      taskReturn: AbiFunction? = null,
    ): AbiFunction {
      check(taskReturn == null || orientation == Export)

      val nameAllocator = NameAllocator()
      val flatParameters = buildList {
        if (isResource) {
          add(flatParameterResourceRep(nameAllocator))
        }
        for (irParameter in irFunction.parameters) {
          add(flatParameter(irParameter.type, irParameter.name, nameAllocator))
        }
      }
      val name = when {
        irFunction.async -> when (orientation) {
          Export -> FunctionName.AsyncExport(irFunction.functionName)
          Import -> FunctionName.AsyncImport(irFunction.functionName)
        }

        else -> irFunction.functionName
      }
      return AbiFunction(
        name = name,
        kotlinIdentifier = irFunction.functionName.kotlinIdentifier,
        documentation = documentation(irFunction, flatParameters),
        parameters = parameters(flatParameters, nameAllocator, name),
        result = result(nameAllocator, irFunction, orientation, taskReturn),
        isSupported = irFunction.isSupported,
        orientation = orientation,
        nameAllocator = nameAllocator,
      )
    }

    fun asyncCallback(
      irFunction: IrFunction,
      orientation: Orientation,
      isResource: Boolean,
    ): AbiFunction {
      val nameAllocator = NameAllocator()
      val flatParameters = buildList {
        if (isResource) {
          add(flatParameterResourceRep(nameAllocator))
        }
        add(flatParameter(WitTypeName.U32, Identifier("event-code"), nameAllocator))
        add(flatParameter(WitTypeName.U32, Identifier("p1"), nameAllocator))
        add(flatParameter(WitTypeName.U32, Identifier("p2"), nameAllocator))
      }

      val name = FunctionName.AsyncExportCallback(irFunction.functionName)
      return AbiFunction(
        name = name,
        kotlinIdentifier = irFunction.functionName.kotlinIdentifier,
        parameters = parameters(flatParameters, nameAllocator, name),
        isSupported = irFunction.isSupported,
        orientation = orientation,
        nameAllocator = nameAllocator,
      )
    }

    fun taskReturn(
      irFunction: IrFunction,
      orientation: Orientation,
    ): AbiFunction {
      val nameAllocator = NameAllocator()
      val flatParameters = buildList {
        val returnType = irFunction.returnType
        if (returnType != null) {
          add(flatParameter(returnType, Identifier("lifted-result"), nameAllocator))
        }
      }

      val name = FunctionName.TaskReturn(irFunction.functionName)
      return AbiFunction(
        name = name,
        kotlinIdentifier = irFunction.functionName.kotlinIdentifier,
        parameters = parameters(flatParameters, nameAllocator, name),
        nameAllocator = nameAllocator,
        isSupported = irFunction.isSupported,
        orientation = orientation,
      )
    }

    fun resourceNew(
      orientation: Orientation,
      resourceType: WitTypeName.Declared,
    ): AbiFunction {
      require(orientation == Export)

      val nameAllocator = NameAllocator()
      val flatParameters = listOf(
        flatParameter(WitTypeName.S32, Identifier("rep"), nameAllocator),
      )
      val result = Result.SingleCoreValueReturn(
        Result.Value(
          encoder = encoderFactory.get(WitTypeName.S32),
          type = kotlinMapper.get(WitTypeName.S32),
        )
      )
      val name = FunctionName.ResourceNew(resourceType)
      return AbiFunction(
        name = name,
        kotlinIdentifier = name.kotlinIdentifier,
        parameters = parameters(flatParameters, nameAllocator, name),
        result = result,
        isSupported = true,
        orientation = Import,
        nameAllocator = nameAllocator,
      )
    }

    fun resourceDrop(
      orientation: Orientation,
      resourceType: WitTypeName.Declared,
    ): AbiFunction {
      val nameAllocator = NameAllocator()
      val flatParameters = listOf(
        flatParameter(WitTypeName.S32, Identifier("index"), nameAllocator),
      )
      val name = FunctionName.ResourceDrop(orientation, resourceType)
      return AbiFunction(
        name = name,
        kotlinIdentifier = name.kotlinIdentifier,
        parameters = parameters(flatParameters, nameAllocator, name),
        isSupported = true,
        orientation = Import,
        nameAllocator = nameAllocator,
      )
    }

    fun resourceClose(
      resourceType: WitTypeName.Declared,
      orientation: Orientation,
    ): AbiFunction {
      val nameAllocator = NameAllocator()
      val flatParameters = listOf(
        flatParameterResourceRep(nameAllocator)
      )
      val name = FunctionName.ResourceClose(resourceType)
      return AbiFunction(
        name = name,
        kotlinIdentifier = name.kotlinIdentifier,
        parameters = parameters(flatParameters, nameAllocator, name),
        isSupported = true,
        orientation = orientation,
        nameAllocator = nameAllocator,
      )
    }

    private fun flatParameter(
      type: WitTypeName,
      name: Identifier,
      nameAllocator: NameAllocator,
    ): FlatParameter {
      val encoder = encoderFactory.get(type)
      val liftedName = nameAllocator.newName(name.lowerCamelCase)
      val liftedType = kotlinMapper.get(type)
      return FlatParameter.Value(
        name = liftedName,
        type = liftedType,
        encoder = encoder,
        loweredSpecs = encoder.coreTypes.withIndex().map { (v, coreType) ->
          val nameHint = encoder.nameHints?.getOrNull(v)
          val coreName = when {
            nameHint != null && encoder.coreTypes.size > 1 ->
              nameAllocator.newName(Identifier("$name-${nameHint.name}").lowerCamelCase)

            v == 0 -> liftedName
            else -> nameAllocator.newName(Identifier("$name${v + 1}").lowerCamelCase)
          }
          ParameterSpec.builder(
            coreName,
            coreType.kotlinCoreType,
          ).build()
        },
      )
    }

    private fun flatParameterResourceRep(nameAllocator: NameAllocator) =
      FlatParameter.ResourceRep(name = nameAllocator.newName("rep"))

    private fun parameters(
      flatParameters: List<FlatParameter>,
      nameAllocator: NameAllocator,
      name: FunctionName,
    ): Parameters {
      val maxFlatParameters = when {
        name is FunctionName.AsyncImport -> 4
        else -> 16
      }
      return when {
        flatParameters.sumOf { it.loweredSpecs.size } <= maxFlatParameters ->
          Parameters.Flattened(flatParameters)

        else -> Parameters.Stored(
          parameters = flatParameters,
          addressSpec = ParameterSpec(
            nameAllocator.newName("parameterAddress"),
            CoreType.Pointer.kotlinCoreType,
          ),
        )
      }
    }

    private fun result(
      nameAllocator: NameAllocator,
      irFunction: IrFunction,
      orientation: Orientation,
      taskReturn: AbiFunction? = null,
    ): Result {
      val returnType = irFunction.returnType
      val value = when {
        returnType == null -> null
        else -> Result.Value(
          encoder = encoderFactory.get(returnType),
          type = kotlinMapper.get(returnType),
        )
      }

      if (taskReturn != null) {
        return Result.CallTaskReturn(value, taskReturn)
      }
      if (value == null) {
        return Result.Void
      }
      if (!irFunction.async) {
        if (value.encoder.coreTypes.size == 1) return Result.SingleCoreValueReturn(value)
        if (orientation == Export) return Result.PointerReturn(value)
      }
      return Result.PointerParameter(
        value,
        ParameterSpec(
          nameAllocator.newName("resultParameter"),
          CoreType.Pointer.kotlinCoreType,
        ),
      )
    }

    private fun documentation(
      value: IrFunction,
      parameters: List<FlatParameter>,
    ): String? {
      val result = buildString {
        val functionDocumentation = value.documentation
        if (functionDocumentation != null) {
          append(functionDocumentation.content.trimIndent())
          append("\n\n")
        }

        for ((index, parameter) in value.parameters.withIndex()) {
          val parameterDocumentation = parameter.documentation ?: continue
          append("@param ${parameters[index].name} ")
          append(parameterDocumentation.content.trimIndent().replace("\n", "\n  "))
          append("\n\n")
        }
      }

      return result.trim().takeIf { it.isNotEmpty() }
    }
  }
}
