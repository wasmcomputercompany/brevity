package dev.wasmo.brevity

/**
 * Converts a WIT encodable type like `wit.wasi.clocks.v0_2_0.WallClock.Datetime` to a preferred
 * application layer type `kotlin.time.Instant`.
 */
interface WitAdapter<W, T> {
  fun toWit(value: T): W
  fun fromWit(wit: W): T
}
