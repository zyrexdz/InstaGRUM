package com.instagrum.local.model

private fun Double.bounded(maximum: Double): Double = if (isFinite()) coerceIn(0.0, maximum) else 0.0

fun SimulationSettings.validated(): SimulationSettings = copy(
    speed = speed.coerceIn(1, 100),
    growth = growth.copy(
        followersPerHour = growth.followersPerHour.bounded(60_000_000.0),
        likesPerHour = growth.likesPerHour.bounded(60_000_000.0),
        commentsPerHour = growth.commentsPerHour.bounded(60_000_000.0),
        storyViewsPerHour = growth.storyViewsPerHour.bounded(60_000_000.0),
        postViewsPerHour = growth.postViewsPerHour.bounded(600_000_000.0),
        reelViewsPerHour = growth.reelViewsPerHour.bounded(600_000_000.0),
        profileVisitsPerHour = growth.profileVisitsPerHour.bounded(60_000_000.0),
    ),
    engagement = engagement.copy(
        multiplier = engagement.multiplier.bounded(100.0),
        likeRate = engagement.likeRate.bounded(1.0),
        commentRate = engagement.commentRate.bounded(1.0),
        visitRate = engagement.visitRate.bounded(1.0),
        followRate = engagement.followRate.bounded(1.0)
    ),
    viralProbabilityPerHour = viralProbabilityPerHour.bounded(100.0),
    liveViewerMultiplier = liveViewerMultiplier.bounded(100.0),
    liveCommentMultiplier = liveCommentMultiplier.bounded(100.0),
    enabledEvents = enabledEvents - EventKind.MANUAL,
)

fun LiveConfig.validated(): LiveConfig {
    val low = minViewers.coerceIn(0, 50_000_000)
    val high = maxViewers.coerceIn(low, 50_000_000)
    return copy(
        title = title.trim().take(100).ifBlank { "Let's catch up" },
        startingViewers = startingViewers.coerceIn(low, high),
        minViewers = low,
        maxViewers = high,
        growthPerMinute = growthPerMinute.bounded(1_000_000.0),
        declinePerMinute = declinePerMinute.bounded(1_000_000.0),
        commentsPerMinute = commentsPerMinute.bounded(10000.0),
        likesPerMinute = likesPerMinute.bounded(1_000_000.0),
        durationMinutes = durationMinutes.coerceIn(1, 1440),
        viralIntensity = viralIntensity.bounded(1.0)
    )
}

/** Bound stored comment samples without orphaning nested replies or losing their ancestry. */
fun List<Comment>.threadWindow(limit: Int = 180): List<Comment> {
    val byId = associateBy { it.id }
    val retained = linkedSetOf<String>()
    val prioritized = filter { it.own }.asReversed() + asReversed()
    for (comment in prioritized) {
        val chain = linkedSetOf<String>()
        var current: Comment? = comment
        while (current != null && chain.add(current.id)) current = byId[current.parentId]
        if ((retained + chain).size <= limit) retained += chain
    }
    return filter { it.id in retained }
}
