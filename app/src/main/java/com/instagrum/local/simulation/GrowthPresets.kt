package com.instagrum.local.simulation

import com.instagrum.local.model.*

/** Product calibration, not Instagram's proprietary algorithm. See docs/GROWTH_RESEARCH.md. */
object GrowthPresets {
    val choices = listOf(
        GrowthPreset.DEAD,
        GrowthPreset.VERY_SLOW,
        GrowthPreset.SLOW,
        GrowthPreset.NORMAL,
        GrowthPreset.MEDIUM,
        GrowthPreset.FAST,
        GrowthPreset.VIRAL,
        GrowthPreset.EXTREME,
        GrowthPreset.CELEBRITY
    )

    fun label(preset: GrowthPreset): String = when (preset) {
        GrowthPreset.DEAD -> "Quiet"
        GrowthPreset.VERY_SLOW -> "Very slow"
        GrowthPreset.SLOW -> "Slow"
        GrowthPreset.NORMAL, GrowthPreset.CUSTOM -> "Natural"
        GrowthPreset.MEDIUM -> "Medium"
        GrowthPreset.FAST -> "Fast"
        GrowthPreset.VIRAL -> "Viral"
        GrowthPreset.EXTREME -> "Breakout"
        GrowthPreset.CELEBRITY -> "Celebrity"
    }

    fun description(preset: GrowthPreset): String = when (preset) {
        GrowthPreset.DEAD -> "An audience that mostly watches. No automatic growth."
        GrowthPreset.VERY_SLOW -> "Long quiet stretches. The occasional new face."
        GrowthPreset.SLOW -> "A small circle, a few reactions, plenty of breathing room."
        GrowthPreset.NORMAL, GrowthPreset.CUSTOM -> "An everyday account. Some posts connect; others stay quiet."
        GrowthPreset.MEDIUM -> "A growing community with regular, unhurried activity."
        GrowthPreset.FAST -> "More people finding you, with busier moments and lulls."
        GrowthPreset.VIRAL -> "Waves of discovery, without making every post a hit."
        GrowthPreset.EXTREME -> "A big audience finding you. Surges still rise and fade."
        GrowthPreset.CELEBRITY -> "Household-name reach. Enormous, immediate, and still uneven."
    }

    fun factor(preset: GrowthPreset) = when (preset) {
        GrowthPreset.DEAD -> 0.0
        GrowthPreset.VERY_SLOW -> .035
        GrowthPreset.SLOW -> .14
        GrowthPreset.NORMAL, GrowthPreset.CUSTOM -> 1.0
        GrowthPreset.MEDIUM -> 2.5
        GrowthPreset.FAST -> 7.0
        GrowthPreset.VIRAL -> 24.0
        GrowthPreset.EXTREME -> 65.0
        GrowthPreset.CELEBRITY -> 260.0
    }

    fun profile(preset: GrowthPreset): GrowthProfile = when (preset) {
        // These are distribution capacities, not guaranteed counters. Posts are
        // intentionally much more discoverable than stories: a story is shown to
        // the existing audience, while a post/reel can enter recommendations.
        GrowthPreset.DEAD -> GrowthProfile(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        GrowthPreset.VERY_SLOW -> GrowthProfile(0.08, 0.0, 0.0, 15.0, 25.0, 60.0, 0.0)
        GrowthPreset.SLOW -> GrowthProfile(0.18, 0.0, 0.0, 50.0, 90.0, 250.0, 0.0)
        GrowthPreset.NORMAL, GrowthPreset.CUSTOM -> GrowthProfile(0.35, 0.0, 0.0, 180.0, 360.0, 1_000.0, 0.0)
        GrowthPreset.MEDIUM -> GrowthProfile(0.9, 0.0, 0.0, 600.0, 1_100.0, 4_000.0, 0.0)
        GrowthPreset.FAST -> GrowthProfile(2.5, 0.0, 0.0, 1_800.0, 4_000.0, 15_000.0, 0.0)
        GrowthPreset.VIRAL -> GrowthProfile(7.0, 0.0, 0.0, 5_000.0, 480_000.0, 1_400_000.0, 0.0)
        GrowthPreset.EXTREME -> GrowthProfile(18.0, 0.0, 0.0, 16_000.0, 1_400_000.0, 4_500_000.0, 0.0)
        GrowthPreset.CELEBRITY -> GrowthProfile(60.0, 0.0, 0.0, 120_000.0, 9_000_000.0, 28_000_000.0, 0.0)
    }

    fun settings(preset: GrowthPreset, current: SimulationSettings = SimulationSettings()) = current.copy(
        preset = preset, growth = profile(preset), speed = 1,
        engagement = EngagementProfile(
            multiplier = 1.0,
            likeRate = .11,
            commentRate = .004,
            visitRate = .018,
            followRate = when (preset) {
                GrowthPreset.DEAD -> 0.0
                GrowthPreset.VERY_SLOW -> .002
                GrowthPreset.SLOW -> .003
                GrowthPreset.NORMAL, GrowthPreset.CUSTOM -> .004
                GrowthPreset.MEDIUM -> .006
                GrowthPreset.FAST -> .008
                GrowthPreset.VIRAL -> .012
                GrowthPreset.EXTREME -> .018
                GrowthPreset.CELEBRITY -> .025
            }
        ),
        viralProbabilityPerHour = when (preset) {
            GrowthPreset.VIRAL -> .35
            GrowthPreset.EXTREME -> .65
            GrowthPreset.CELEBRITY -> 1.1
            GrowthPreset.FAST -> .10
            else -> .004 * factor(preset)
        },
    )
}
