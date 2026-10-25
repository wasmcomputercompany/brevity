package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.abi.AbiInterface
import dev.wasmo.brevity.kotlin.abi.AbiResource
import dev.wasmo.brevity.kotlin.abi.AbiService
import dev.wasmo.brevity.kotlin.abi.AbiWorld

/**
 * Decides what the implementation classes are named for services.
 */
abstract class TypeNamer {
  fun implementation(abiService: AbiService): ClassName {
    return when (abiService) {
      is AbiWorld -> implementation(abiService)
      is AbiInterface -> implementation(abiService)
      is AbiResource -> implementation(abiService)
    }
  }

  abstract fun implementation(abiWorld: AbiWorld): ClassName

  abstract fun implementation(abiInterface: AbiInterface): ClassName

  fun implementation(service: AbiResource) = implementation(service.type)

  /** Returns the implementation class for a resource type. */
  abstract fun implementation(type: TypeName.Declared): ClassName
}
