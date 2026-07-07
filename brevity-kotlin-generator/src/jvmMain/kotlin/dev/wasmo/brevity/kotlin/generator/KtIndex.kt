package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.ir.IrTypeName

class KtIndex(services: List<KtService>) {
  val types: Map<IrTypeName.Declared, KtTypeDeclaration> = buildMap {
    for (service in services) {
      for (type in service.types) {
        put(type.ktType.witType, type)
      }
    }
  }

  val services: Map<ServiceName, KtService> = buildMap {
    for (service in services) {
      put(service.serviceName, service)
    }
  }

  operator fun get(serviceName: ServiceName): KtService? = services[serviceName]

  operator fun get(typeName: IrTypeName.Declared): KtTypeDeclaration? = types[typeName]
}
