package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.ir.IrCase
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrField
import dev.wasmo.brevity.ir.IrFlag
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrParameter
import dev.wasmo.brevity.kotlin.encoders.CoreType

const val kotlinPackagePrefix: String = "wit"

val IrCase.kotlinName: String
  get() = name.upperCamelCase

val IrParameter.kotlinName: String
  get() = name.lowerCamelCase

val IrField.kotlinName: String
  get() = name.lowerCamelCase

val IrFlag.kotlinName: String
  get() = name.lowerCamelCase

val FunctionName.kotlinName: String
  get() = kotlinIdentifier.lowerCamelCase

private val FunctionName.kotlinIdentifier: Identifier
  get() {
    return when (this) {
      is FunctionName.ResourceDrop -> dropFunctionName
      is FunctionName.Constructor -> name
      is FunctionName.Interface -> name
      is FunctionName.Method -> name
      is FunctionName.Static -> name
      is FunctionName.World -> name
      is FunctionName.TaskReturn -> Identifier("${original.kotlinIdentifier}-$taskReturnSuffix")
      is FunctionName.AsyncLift -> original.kotlinIdentifier
      is FunctionName.AsyncLiftCallback ->
        Identifier("${original.kotlinIdentifier}-$asyncLiftCallbackSuffix")
    }
  }

val IrExternalApi.instanceName: String
  get() = (plainName ?: serviceName.name).lowerCamelCase

val ServiceName.bridgeType: ClassName
  get() = ClassName(
    kotlinApi.packageName,
    "Bridge${name.upperCamelCase}",
  )

val CoreType.kotlinCoreType: KtTypeName
  get() = when (this) {
    CoreType.I32 -> INT
    CoreType.I64 -> LONG
    CoreType.F32 -> FLOAT
    CoreType.F64 -> DOUBLE
    CoreType.Pointer -> INT
  }

/** Returns true if we've done the work to implement this. */
val IrFunction.isSupported: Boolean
  get() = when (functionName) {
    is FunctionName.Constructor -> false
    is FunctionName.Static -> false
    else -> true
  }
