package dev.wasmo.brevity

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store

abstract class World<H, G> {
  abstract val host: H
  abstract val guest: G

  @BrevityInternalApi
  abstract fun initImports(bridge: HostBridge, store: Store)

  @BrevityInternalApi
  abstract fun initExports(instance: Instance)
}
