// Copyright 2019-2023 the Contributors to the WASI Specification
package wit.wasi.v0_1

enum class ClockId {
  /** The clock measuring real time. Time value zero corresponds with 1970-01-01T00:00:00Z. */
  realtime,

  /**
   * The store-wide monotonic clock, which is defined as a clock measuring real time, whose value
   * cannot be adjusted and which cannot have negative clock jumps. The epoch of this clock is
   * undefined. The absolute time value of this clock therefore has no meaning.
   */
  monotonic,

  /** The CPU-time clock associated with the current process. */
  process_cputime_id,

  /** The CPU-time clock associated with the current thread. */
  thread_cputime_id,
}
