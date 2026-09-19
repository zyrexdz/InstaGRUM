package com.instagrum.local.data

import com.instagrum.local.model.*
import com.instagrum.local.simulation.GrowthPresets

object InitialState {
    fun create(now: Long) = AppState(
        schemaVersion = 2,
        profile = Profile(), settings = GrowthPresets.settings(GrowthPreset.NORMAL),
        lastSavedAt = now, lastSimulationAt = now, profileCreated = false
    )

    /** Preserve old content, but require ownership setup instead of silently retaining the demo identity. */
    fun migrate(state: AppState): AppState {
        // Background catch-up became the default; profiles saved before that opt
        // in once, and can still turn it off in settings.
        val base = if (state.schemaVersion < 2) state.copy(
            schemaVersion = 2,
            settings = state.settings.copy(backgroundActivity = true)
        ) else state
        return if (base.profileCreated) base else base.copy(
            settings = GrowthPresets.settings(GrowthPreset.NORMAL, base.settings),
            session = UiSession(), activeLive = null,
        )
    }
}
