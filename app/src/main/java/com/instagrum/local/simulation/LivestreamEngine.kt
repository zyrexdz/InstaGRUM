package com.instagrum.local.simulation

import com.instagrum.local.model.*
import kotlin.math.*

object LivestreamEngine {
    /** Seconds already include simulation speed. UI uses 1x; test tools can still accelerate time. */
    fun step(
        session: LiveSession,
        seconds: Double,
        settings: SimulationSettings,
        now: Long,
        accountFollowers: Long = 0L,
    ): LiveSession {
        if (session.endedAt != null || settings.paused || settings.frozen || !seconds.isFinite() || seconds <= 0) return session
        var current = session
        var left = seconds.coerceAtMost(3600.0)
        while (left > 0 && current.endedAt == null) {
            val dt = min(1.0, left)
            current = tick(
                current,
                dt,
                settings,
                now - ((left - dt) * 1000).toLong(),
                accountFollowers.coerceAtLeast(0L)
            )
            left -= dt
        }
        return current
    }

    private fun tick(
        s: LiveSession,
        dt: Double,
        settings: SimulationSettings,
        now: Long,
        accountFollowers: Long,
    ): LiveSession {
        val rng = SimRandom(s.seed)
        val c = s.config
        val elapsed = (s.elapsedSeconds + dt).coerceAtMost(c.durationMinutes * 60.0)
        val pace = GrowthPresets.factor(settings.preset)
        val accountFollowerCount = accountFollowers.toDouble()
        val followerCapacity = if (pace <= 0.0 || accountFollowerCount <= 0.0) 0.0 else accountFollowerCount * (
                .04 + sqrt(pace) * .008
                )
        val discoveryBase = when (settings.preset) {
            GrowthPreset.DEAD -> 0.0
            GrowthPreset.VERY_SLOW -> .5
            GrowthPreset.SLOW -> 1.0
            GrowthPreset.NORMAL, GrowthPreset.CUSTOM -> 3.0
            GrowthPreset.MEDIUM -> 8.0
            GrowthPreset.FAST -> 18.0
            GrowthPreset.VIRAL -> 45.0
            GrowthPreset.EXTREME -> 120.0
        }
        // Live discovery is bounded by the account's network. High pace can
        // open a recommendation lane, but 100 followers should not create a
        // thousand-person room by accident.
        val discoveryCapacity = discoveryBase * (1.0 + ln(accountFollowerCount + 1.0) * .35)
        val configuredCapacity = maxOf(c.minViewers, c.startingViewers, s.viewers)
        val naturalCapacity = maxOf(
            configuredCapacity.toDouble(),
            (followerCapacity + discoveryCapacity) * settings.liveViewerMultiplier.coerceAtLeast(0.0)
        )
        val timing = HumanTiming(rng, s.carry)
        var audience = s.audience
        if (!s.initialized && s.viewers > 0) {
            audience = (0 until s.viewers.coerceAtMost(2000)).map { i ->
                LiveViewer(
                    CommentGenerator.person(i + 720),
                    0.0,
                    rng.logNormal(150.0, 1.1).coerceIn(12.0, 1800.0),
                    rng.next() < .18
                )
            }
        }
        var phase = s.phaseMultiplier
        var phaseUntil = s.phaseUntil
        if (phaseUntil <= elapsed) {
            phase = if (rng.next() < .25) rng.between(.12, .5) else rng.between(.65, 1.65)
            phaseUntil = elapsed + rng.logNormal(45.0, .5).coerceIn(15.0, 150.0)
        }
        var surgeUntil = s.surgeUntil
        if (settings.randomEvents && EventKind.LIVE_SPIKE in settings.enabledEvents && pace > 0 &&
            timing.events("live-wave", dt * c.viralIntensity * pace / 10000) > 0
        ) surgeUntil = elapsed + 180
        val surge =
            if (surgeUntil > elapsed) 1 + 3 * sin(PI * ((surgeUntil - elapsed) / 180).coerceIn(0.0, 1.0)) else 1.0
        // The room starts empty. Discovery ramps up; nothing adds the same batch every second.
        val ramp = if (elapsed < 4) 0.0 else (1 - exp(-(elapsed - 4) / 55))
        val lateFade = (1 - .6 * (elapsed / (c.durationMinutes * 60.0)).pow(3)).coerceAtLeast(.25)
        val capacity = c.maxViewers.coerceIn(0, 10_000).coerceAtMost(
            naturalCapacity.roundToInt().coerceAtLeast(c.minViewers)
        )
        val room = if (capacity <= 0) 0.0 else ((capacity - audience.size).toDouble() / capacity).coerceIn(0.0, 1.0)
        val arrivalRate = (
                (.08 + sqrt(pace.coerceAtLeast(0.0)) * .035) *
                        (c.growthPerMinute / 65).coerceIn(0.0, 10.0) *
                        settings.liveViewerMultiplier.coerceAtLeast(0.0) * phase * ramp * surge * lateFade * room
                ).coerceAtLeast(0.0)
        val arrivals = timing.events("arrivals", dt * arrivalRate, 12)
        val departed = audience.filter { it.leavesAt <= elapsed }
        audience = audience.filter { it.leavesAt > elapsed }
        val newViewers = (0 until arrivals.coerceAtMost((capacity - audience.size).coerceAtLeast(0))).map { i ->
            val actor = CommentGenerator.person((rng.seed and 0x7fffffff).toInt().mod(90000) + i)
            rng.next()
            val stay = rng.logNormal(150.0 / (c.declinePerMinute / 35.0).coerceIn(.2, 5.0), 1.05).coerceIn(8.0, 2400.0)
            LiveViewer(actor, elapsed, elapsed + stay, rng.next() < .22)
        }
        audience = (audience + newViewers).distinctBy { it.person.id }
        val chatters = audience.filter { it.talkative && elapsed - it.joinedAt > 6 }
        val chatCount = timing.events(
            "chat",
            dt * chatters.size * .024 * (c.commentsPerMinute / 35).coerceIn(0.0, 20.0) * settings.liveCommentMultiplier,
            8
        )
        var chat = s.chat
        repeat(if (chatters.isEmpty()) 0 else chatCount) { i ->
            val person = chatters[rng.int(chatters.size)].person
            val comment = CommentGenerator.generate(
                rng.seed,
                now,
                "${s.id}:$elapsed:$i",
                CommentContext(caption = c.title, mediaKind = MediaKind.VIDEO),
                live = true,
                recentTexts = chat.map { it.text }
            ).copy(person = person)
            rng.next()
            chat = (chat + comment).takeLast(80)
        }
        val likes = timing.events("likes", dt * audience.size * .008 * (c.likesPerMinute / 160).coerceIn(0.0, 20.0), 20)
        val alreadyFollowed = s.gainedFollowers.map { it.id }.toSet()
        val followActors =
            departed.filter { elapsed - it.joinedAt > 45 && it.person.id !in alreadyFollowed && rng.next() < .025 }
                .map { it.person.copy(followsYou = true) }
        val followers = followActors.size.toLong()
        return s.copy(
            elapsedSeconds = elapsed,
            viewers = audience.size,
            peakViewers = max(s.peakViewers, audience.size),
            audience = audience,
            likes = s.likes.plusStat(likes.toLong()),
            totalComments = s.totalComments.plusStat(if (chatters.isEmpty()) 0 else chatCount.toLong()),
            newFollowers = s.newFollowers.plusStat(followers),
            gainedFollowers = (s.gainedFollowers + followActors).distinctBy { it.id },
            chat = chat,
            seed = rng.seed,
            carry = timing.clocks,
            phaseMultiplier = phase,
            phaseUntil = phaseUntil,
            initialized = true,
            surgeUntil = surgeUntil,
            endedAt = if (elapsed >= c.durationMinutes * 60) now else null
        )
    }
}
