package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.generator.kotlinApi

sealed interface FunctionParent {
  val orientation: Orientation

  data class Resource(
    override val orientation: Orientation,
    val type: TypeName.Declared,
    val kotlinType: ClassName,
    val handleType: ClassName,
  ) : FunctionParent

  data class World(
    override val orientation: Orientation,
    val serviceName: ServiceName,
  ) : FunctionParent {
    val type: KtTypeName
      get() = when (orientation) {
        Orientation.Export -> serviceName.kotlinApi.nestedClass("Guest")
        Orientation.Import -> serviceName.kotlinApi.nestedClass("Host")
      }
  }

  data class Interface(
    override val orientation: Orientation,
    val serviceName: ServiceName,
    val instanceName: String,
  ) : FunctionParent {
    val type: KtTypeName
      get() = serviceName.kotlinApi
  }
}
