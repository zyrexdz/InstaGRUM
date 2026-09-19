package com.instagrum.local.simulation

import kotlin.math.*

class HumanTiming(private val random: SimRandom, previous: Map<String, Double>) {
    val clocks = previous.toMutableMap()
    fun events(key: String, expected: Double, limit: Int = 30): Int {
        if (!expected.isFinite() || expected <= 0 || limit <= 0) return 0

        if (expected >= 20.0) {
            val gaussian = (0 until 12).sumOf { random.next() } - 6.0
            val sampled = (expected + sqrt(expected) * gaussian).roundToInt().coerceIn(0, limit)
            clocks[key] = random.waiting()
            return sampled
        }
        var left = (clocks[key] ?: random.waiting()) - expected
        var count = 0
        while (left <= 0 && count < limit) {
            count++; left += random.waiting()
        }
        clocks[key] = left.coerceAtLeast(0.00001)
        return count
    }
}

fun SimRandom.waiting(): Double = -ln((1.0 - next()).coerceIn(1e-10, .9999999999))
fun SimRandom.logNormal(median: Double, spread: Double): Double {
    val gaussian = sqrt(-2 * ln(next().coerceAtLeast(1e-10))) * cos(2 * PI * next())
    return median * exp(gaussian * spread)
}

fun contentAppeal(id: String, viralPotential: Int = 35): Double {
    val seedAppeal = .45 + (id.hashCode().toLong().and(0x7fffffff) % 1000) / 700.0
    val quality = (.70 + viralPotential.coerceIn(0, 100) / 100.0 * .55)
    return (seedAppeal * quality).coerceIn(.25, 2.5)
}

fun contentAttention(ageSeconds: Double, reel: Boolean): Double {

    if (ageSeconds < 4) return 0.0
    val ramp = 1 - exp(-(ageSeconds - 4) / if (reel) 70.0 else 48.0)
    val decay = (1 + ageSeconds / if (reel) 3600.0 else 2400.0).pow(if (reel) -1.35 else -1.5)
    return (ramp * decay).coerceIn(0.0, 1.0)
}
