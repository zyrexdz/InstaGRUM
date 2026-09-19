package com.instagrum.local.simulation

import com.instagrum.local.model.*

object SeedData {
    fun create(now: Long): AppState {
        val captions = listOf("Somewhere between the mountains and a deep breath. 🌿", "Slow mornings, warm light.", "A little further from the noise.", "Taking the long way home.", "A city of a thousand little stories.", "No plans. Just this.", "Keeping a little piece of summer.", "Good things take their time.", "Same sky, a different perspective.")
        val locations = listOf("Dolomites, Italy", "At home", "Pacific coast", "On the road", "New York", "The great outdoors", "Golden hour", "A quiet corner", "Somewhere beautiful")
        val posts = captions.mapIndexed { i, text ->
            val time = now - (i + 1) * 86400000L
            val comments = (0 until 12).map { j -> CommentGenerator.generate(1942L + i * 500 + j * 913, time + j * 60000, "seed-$i-$j") }
            Post("post-$i", Media(artwork = i), text, locations[i], time, 1243L + (8 - i) * 137, 18L + i * 3, 18042L - i * 1251, comments, viralPotential = 72 - i * 5)
        }
        return AppState(
            posts = posts,
            stories = listOf(
                Story("story-0", Media(artwork = 0), "a little reset 🌿", now - 1800000, now + 84600000, 248, 2400),
                Story("story-1", Media(artwork = 2), "stay a little longer", now - 1200000, now + 85200000, 193, 1800),
                Story("highlight-0", Media(artwork = 3), "the places we keep", now - 86400000L * 5, now - 86400000, 2318, 2500, "Travel"),
                Story("highlight-1", Media(artwork = 1), "ordinary magic", now - 86400000L * 4, now - 86400000, 1830, 1900, "Everyday"),
                Story("highlight-2", Media(artwork = 6), "chasing the light", now - 86400000L * 3, now - 86400000, 2604, 2800, "Golden"),
            ),
            people = (0 until 80).map { CommentGenerator.person(it * 7).copy(followed = it < 12) },
            events = listOf(GrowthEvent("welcome", EventKind.MANUAL, "Your own little corner", "Welcome to your private simulator. Every follower, view and interaction here is local and fictional.", now)),
            lastSavedAt = now,
        )
    }
}
