@file:OptIn(ExperimentalWasmInterop::class)

package dev.wasmo.brevity

/**
 * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-waitable-setnew
 */
@WasmImport(module = $$"$root", name = "[waitable-set-new]")
internal external fun newWaitableSet(): Int

/**
 * Note that a waitable can be in at most one set. Joining a set removes the waitable from its
 * current set, if any. Joining waitable 0 removes it from all sets.
 */
@WasmImport(module = $$"$root", name = "[waitable-join]")
internal external fun waitableJoin(waitableIndex: Int, waitableSet: Int)

/**
 * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-waitable-setpoll
 */
@WasmImport(module = $$"$root", name = "[waitable-set-poll]")
internal external fun waitableSetPoll_export(waitableSet: Int, eventPointer: Int): Int

@WasmImport(module = $$"$root", name = "[waitable-set-drop]")
internal external fun waitableSetDrop_export(waitableSet: Int)

@WasmImport(module = $$"$root", name = "[context-get-0]")
internal external fun contextGet0_export(): Int

@WasmImport(module = $$"$root", name = "[context-get-1]")
internal external fun contextGet1_export(): Int

@WasmImport(module = $$"$root", name = "[context-set-0]")
internal external fun contextSet0_export(value: Int)

@WasmImport(module = $$"$root", name = "[context-set-1]")
internal external fun contextSet1_export(value: Int)

/**
 * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-taskcancel
 */
@WasmImport(module = $$"[export]$root", name = "[task-cancel]")
internal external fun taskCancel()

/**
 * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-subtaskcancel
 */
@WasmImport(module = $$"$root", name = "[subtask-cancel]")
internal external fun subtaskCancel(subtask: Int): Int

/**
 * https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#-canon-subtaskdrop
 */
@WasmImport(module = $$"$root", name = "[subtask-drop]")
internal external fun subtaskDrop(subtask: Int)
