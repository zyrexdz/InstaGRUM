package com.instagrum.local.simulation

class SimRandom(seed: Long) {
    var seed: Long = if (seed == 0L) 814729L else seed
        private set
    fun next(): Double {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        seed = x
        return (x ushr 11).toDouble() / 9007199254740992.0
    }
    fun int(bound: Int): Int = (next() * bound.coerceAtLeast(1)).toInt().coerceAtMost(bound.coerceAtLeast(1) - 1)
    fun between(low: Double, high: Double) = low + next() * (high - low)
}

internal const val MAX_STAT = 9_000_000_000_000L
internal fun Long.plusStat(value: Long) = (this.coerceIn(0, MAX_STAT) + value.coerceIn(0, MAX_STAT)).coerceAtMost(MAX_STAT)

internal class Accumulator(previous: Map<String, Double>) {
    val carry = previous.toMutableMap()
    fun add(key: String, amount: Double): Long {
        val total = (carry[key] ?: 0.0) + (if (amount.isFinite()) amount else 0.0).coerceIn(0.0, MAX_STAT.toDouble())
        val whole = total.toLong()
        carry[key] = total - whole
        return whole
    }
}
