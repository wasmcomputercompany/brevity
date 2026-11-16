package dev.wasmo.brevity

/**
 * This is an implementation detail of `wasm-tools` and shouldn't be here in Brevity. We should
 * delete this once we properly support loading components composed of multiple core modules.
 *
 * This file is a derivative work of bytecodealliance/wasm-tools and subject to that project's
 * Apache 2 license.
 *
 * https://github.com/bytecodealliance/wasm-tools/pull/2603
 */
@BrevityInternalApi
enum class WasmTaskHookLocation {
  /** At the start of a synchronously lifted function. */
  LiftedFunctionStart,

  /** At the end of a synchronously lifted function (before post-return if that's specified). */
  LiftedFunctionEnd,

  /** At the start of an async-lifted function, either stackful or not. */
  AsyncLiftedFunctionStart,

  /** At the start of an async-lifted's callback option. */
  AsyncLiftedFunctionCallbackStart,

  /**
   * When an async-lifted entrypoint returns, or a callback option returns, and the task is blocking
   * or yielding.
   */
  AsyncLiftedFunctionSuspend,

  /** When an async-lifted function completes. */
  AsyncLiftedFunctionEnd,

  /** When `_initialize` is originally invoked. */
  InitializeStart,

  /** When `_initialize` completes. */
  InitializeEnd,

  /** When a resource destructor is invoked. */
  DestructorStart,

  /** When a resource destructor completes. */
  DestructorEnd,

  /** When a post-return hook is invoked. */
  PostReturnHookStart,

  /** When a post-return hook completes. */
  PostReturnHookEnd,
}
