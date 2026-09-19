package com.instagrum.local.simulation

import com.instagrum.local.model.*
import kotlin.math.*

object SimulationEngine {
    fun step(state: AppState, realSeconds: Double, now: Long): AppState {
        if (state.settings.paused || state.settings.frozen || !state.profileCreated || realSeconds <= 0 || !realSeconds.isFinite()) return state
        var result = state
        val speed = state.settings.speed.coerceIn(1, 100)
        var remaining = (realSeconds * speed).coerceAtMost(3600.0)
        while (remaining > 0) {
            val quantum = when {
                remaining > 1800 -> 30.0
                remaining > 300 -> 10.0
                else -> 1.0
            }
            val dt = min(quantum, remaining)
            result = tick(result, dt, now - ((remaining - dt) / speed * 1000).toLong())
            remaining -= dt
        }
        return result
    }

    fun viralMultiplier(event: ViralEvent, time: Double): Double {
        val progress = (time - event.startedAt) / event.durationSeconds.coerceAtLeast(1.0)
        if (progress !in 0.0..1.0) return 1.0
        val envelope = if (progress < .15) progress / .15 else exp(-(progress - .15) * 5) * (1 - progress) / .85
        return 1 + event.strength.coerceIn(0.0, 60.0) * envelope
    }

    private fun tick(s: AppState, dt: Double, now: Long): AppState {
        val rng = SimRandom(s.engine.seed)
        val timing = HumanTiming(rng, s.engine.clocks)
        val settings = s.settings
        val g = settings.growth
        val e = settings.engagement
        val elapsed = s.engine.elapsedSeconds + dt
        var nextActor = s.engine.nextActor
        fun person(): FakePerson {
            val returning = s.people.filter { it.id != "self" }
            return if (returning.isNotEmpty() && rng.next() < .08) returning[rng.int(returning.size)]
            else CommentGenerator.person(nextActor++.mod(2_000_000) + 100)
        }

        var phaseUntil = s.engine.phaseUntil
        var phase = s.engine.phaseMultiplier
        if (phaseUntil <= elapsed) {
            phase = when {
                rng.next() < .22 -> rng.between(.08, .35)
                rng.next() < .15 -> rng.between(1.5, 2.7)
                else -> rng.between(.55, 1.25)
            }
            phaseUntil = elapsed + rng.logNormal(110.0, .65).coerceIn(30.0, 420.0)
        }
        // Gradual day/night influence; never make every counter move on the same tick.
        val dayPosition = (now % 86400000L) / 86400000.0
        val dayFactor = .7 + .3 * cos(2 * PI * (dayPosition - .75))
        val followerCount = s.profile.followers.coerceAtLeast(0L).toDouble()
        val traffic = phase * dayFactor * dt / 3600
        var viral =
            s.engine.events.filter { it.startedAt + it.durationSeconds > elapsed && s.posts.any { p -> p.id == it.postId } }
        var events = s.events
        var cooldown = s.engine.eventCooldown
        var storySurgeUntil = s.engine.storySurgeUntil
        var floodUntil = s.engine.commentFloodUntil
        var ambientUntil = s.engine.ambientUntil
        var ambient = if (ambientUntil > elapsed) s.engine.ambientMultiplier else 1.0
        var live = s.activeLive
        val hasContent = s.posts.isNotEmpty() || (followerCount > 0 && s.stories.any { it.expiresAt > now })
        val types = EventKind.entries.filter {
            it in settings.enabledEvents && when (it) {
                EventKind.MANUAL -> false
                EventKind.VIRAL, EventKind.REPOST, EventKind.REEL_SPIKE -> s.posts.any { post -> !post.frozen && (it != EventKind.REEL_SPIKE || post.media.kind != MediaKind.IMAGE) }
                EventKind.LIVE_SPIKE, EventKind.RAID -> live != null
                EventKind.STORY_SPIKE -> s.stories.any { story -> story.expiresAt > now }
                else -> hasContent
            }
        }
        if (g.postViewsPerHour > 0 && settings.randomEvents && types.isNotEmpty() && elapsed > cooldown && timing.events(
                "random-event",
                settings.viralProbabilityPerHour * dt / 3600
            ) > 0
        ) {
            val kind = types[rng.int(types.size)]
            val eligible =
                s.posts.filter { !it.frozen && (kind != EventKind.REEL_SPIKE || it.media.kind != MediaKind.IMAGE) }
            val post = eligible.getOrNull(rng.int(eligible.size))
            when (kind) {
                EventKind.STORY_SPIKE -> storySurgeUntil = elapsed + 600
                EventKind.COMMENT_FLOOD -> floodUntil = elapsed + 120
                EventKind.ENGAGEMENT_DROP -> {
                    ambient = .3; ambientUntil = elapsed + 600
                }

                EventKind.LIVE_SPIKE, EventKind.RAID -> live = live?.copy(surgeUntil = live.elapsedSeconds + 180)
                else -> if (post != null) viral = viral + ViralEvent(post.id, elapsed, 2400.0, rng.between(2.0, 6.0))
            }
            events = (listOf(
                GrowthEvent(
                    "event-${rng.seed}",
                    kind,
                    "Your audience is changing",
                    "A new wave of attention",
                    now,
                    post?.id
                )
            ) + events).take(100)
            cooldown = elapsed + 600
        }
        var pending = s.engine.pending.toMutableList()
        var activity = s.activity
        var followers = s.profile.followers
        var visits = s.profile.visits
        var people = s.people
        val followerIds = s.engine.followerIds.toMutableSet()
        fun addActivity(
            kind: InteractionKind,
            actor: FakePerson,
            id: String,
            postId: String? = null,
            storyId: String? = null,
            text: String = ""
        ) {
            activity = (listOf(SocialActivity(id, kind, actor, now, postId, storyId, text)) + activity).take(250)
            val existing = people.find { it.id == actor.id }
            val updated = (existing ?: actor).copy(followsYou = actor.id in followerIds)
            people = (people.filterNot { it.id == actor.id } + updated).takeLast(400)
        }

        fun schedule(kind: InteractionKind, actor: FakePerson, postId: String? = null, storyId: String? = null) {
            if (pending.size >= 800) return
            val delay = when (kind) {
                InteractionKind.LIKE -> rng.logNormal(12.0, .8).coerceIn(3.0, 160.0)
                InteractionKind.COMMENT, InteractionKind.STORY_REPLY -> rng.logNormal(85.0, .8).coerceIn(20.0, 600.0)
                InteractionKind.FOLLOW -> rng.logNormal(95.0, .9).coerceIn(15.0, 900.0)
                InteractionKind.STORY_REACTION -> rng.between(4.0, 35.0)
            }
            pending += PendingInteraction(
                "interaction-${rng.seed}-$nextActor-${pending.size}",
                elapsed + delay,
                kind,
                actor,
                postId,
                storyId
            )
        }

        val posts = s.posts.map { post ->
            if (post.frozen) return@map post
            val age = (elapsed - post.publishedAtSimulation).coerceAtLeast(0.0)
            val distributionRate = if (post.media.kind == MediaKind.REEL) g.reelViewsPerHour else g.postViewsPerHour
            val wave = viral.filter { it.postId == post.id }.maxOfOrNull { viralMultiplier(it, elapsed) } ?: 1.0
            val appeal = contentAppeal(post.id, post.viralPotential)
            val attention = contentAttention(age, post.media.kind == MediaKind.REEL)
            val postWave = if (post.media.kind == MediaKind.REEL) 1.25 else 1.0
            // A post receives a follower test plus recommendation distribution.
            // The latter exists even at zero followers, and is deliberately noisy:
            // pace is a distribution environment, not a guaranteed result.
            // Reach per hour scales with the audience size; a 10M account puts a
            // new post in front of millions within minutes, which is why huge
            // accounts collect six-figure like counts almost immediately.
            val followerRate = if (distributionRate <= 0.0) 0.0 else (
                    followerCount * (1.4 + sqrt(GrowthPresets.factor(settings.preset)) * 5.5)
                    ).coerceAtMost(2_000_000_000.0)
            val observedLikes = post.likes.toDouble() / post.views.coerceAtLeast(1L)
            val observedComments = post.commentCount.toDouble() / post.views.coerceAtLeast(1L)
            // A brand-new post has no signal yet, so it must not be judged as a
            // weak one. Momentum only takes over once real reactions exist.
            val measured = ((observedLikes / .08) * .72 + (observedComments / .006) * .28).coerceIn(.35, 2.4)
            val confidence = (post.views.toDouble() / 400.0).coerceIn(0.0, 1.0)
            val engagementSignal = 1.0 + (measured - 1.0) * confidence
            val launchSignal = if (age < 150.0) 1.0 + (post.viralPotential / 100.0) * .25 else 1.0
            val discoveryRate = distributionRate * appeal * postWave * engagementSignal * launchSignal
            val opportunity = (
                    (followerRate + discoveryRate) * attention *
                            traffic * ambient * (0.78 + attention * .22) * wave
                    ).coerceAtLeast(0.0) / sqrt(s.posts.size.coerceAtLeast(1).toDouble())
            val count = timing.events("view:${post.id}", opportunity, 2_000_000)
            val commentRate = e.commentRate * e.multiplier * if (floodUntil > elapsed) 3 else 1
            val followChance = (e.followRate * e.multiplier * (0.55 + appeal * .45) *
                    if (post.media.kind == MediaKind.REEL) 1.25 else 1.0).coerceIn(0.0, .45)
            val namedViews = count.coerceAtMost(60)
            repeat(namedViews) {
                val actor = person()
                if (rng.next() < e.likeRate * e.multiplier && actor.id !in post.likerIds && pending.none { p -> p.postId == post.id && p.person.id == actor.id && p.kind == InteractionKind.LIKE }) schedule(
                    InteractionKind.LIKE,
                    actor,
                    postId = post.id
                )
                if (rng.next() < commentRate) schedule(InteractionKind.COMMENT, actor, postId = post.id)
                if (rng.next() < e.visitRate) visits = visits.plusStat(1)
                if (rng.next() < followChance && actor.id !in followerIds && pending.none { it.kind == InteractionKind.FOLLOW && it.person.id == actor.id }) schedule(
                    InteractionKind.FOLLOW,
                    actor
                )
            }
            val anonymousViews = (count - namedViews).coerceAtLeast(0)
            val bulkLikes = timing.events("bulk-like:${post.id}", anonymousViews * e.likeRate * e.multiplier, 2_000_000)
            val bulkComments = timing.events("bulk-comment:${post.id}", anonymousViews * commentRate, 500_000)
            val bulkVisits = timing.events("bulk-visit:${post.id}", anonymousViews * e.visitRate, 500_000)
            val bulkFollows = timing.events("bulk-follow:${post.id}", anonymousViews * followChance, 500_000)
            visits = visits.plusStat(bulkVisits.toLong())
            followers = followers.plusStat(bulkFollows.toLong())
            post.copy(
                views = post.views.plusStat(count.toLong()),
                likes = post.likes.plusStat(bulkLikes.toLong()),
                commentCount = post.commentCount.plusStat(bulkComments.toLong())
            )
        }.toMutableList()
        val stories = s.stories.map { story ->
            if (story.expiresAt <= now || story.views >= story.targetViews) return@map story
            val age = (elapsed - story.publishedAtSimulation).coerceAtLeast(0.0)
            val attention = if (age < 12) 0.0 else (1 - exp(-(age - 12) / 180)) * (1 + age / 3600).pow(-1.3)
            val wave = if (storySurgeUntil > elapsed) 1 + 3 * ((storySurgeUntil - elapsed) / 600) else 1.0
            // Stories stay inside the follower network, but a real account shows
            // a story to a large share of its followers within the first hours.
            // Pace changes how fast they arrive, never whether they exist.
            val storyAudience = if (followerCount <= 0.0) 0.0 else
                followerCount * (.55 + sqrt(GrowthPresets.factor(settings.preset)) * .35)
            val count =
                timing.events("story:${story.id}", storyAudience * traffic * attention * wave, 2_000_000).toLong()
                    .coerceAtMost(story.targetViews - story.views).toInt()
            val namedStoryViews = count.coerceAtMost(40)
            var sample = story.viewers
            var insights = story.insights
            val reactions = story.reactionCounts.toMutableMap()
            repeat(namedStoryViews) {
                var actor = person()
                if (sample.any { it.person.id == actor.id }) actor = CommentGenerator.person(nextActor++ + 10000)
                val reaction = if (rng.next() < .07) listOf("❤️", "🔥", "😍", "👏")[rng.int(4)] else ""
                if (reaction.isNotEmpty()) reactions[reaction] = (reactions[reaction] ?: 0L) + 1
                sample = (listOf(StoryVisit(actor, now, reaction)) + sample).take(150)
                val navigation = rng.next()
                insights = insights.copy(
                    impressions = insights.impressions + 1,
                    likes = insights.likes + if (reaction.isNotEmpty()) 1 else 0,
                    forward = insights.forward + if (navigation < .62) 1 else 0,
                    exited = insights.exited + if (navigation >= .62 && navigation < .77) 1 else 0,
                    nextStory = insights.nextStory + if (navigation >= .77 && navigation < .92) 1 else 0,
                    back = insights.back + if (navigation >= .92) 1 else 0
                )
                if (rng.next() < .04) insights = insights.copy(impressions = insights.impressions + 1)
                if (rng.next() < .014) insights =
                    insights.copy(profileVisits = insights.profileVisits + 1).also { visits = visits.plusStat(1) }
                if (story.overlay.sticker.isNotBlank() && rng.next() < .035) insights =
                    insights.copy(stickerTaps = insights.stickerTaps + 1)
                if (rng.next() < .009) insights = insights.copy(shares = insights.shares + 1)
                if (reaction.isNotEmpty()) addActivity(
                    InteractionKind.STORY_REACTION,
                    actor,
                    "reaction-${rng.seed}-$nextActor",
                    storyId = story.id,
                    text = reaction
                )
                if (rng.next() < .008) schedule(InteractionKind.STORY_REPLY, actor, storyId = story.id)
            }
            // Viewers beyond the named sample still count toward the totals, so a
            // large account sees realistic insights without storing every person.
            val anonymousStoryViews = (count - namedStoryViews).coerceAtLeast(0)
            if (anonymousStoryViews > 0) {
                val bulkReactions = timing.events("story-react:${story.id}", anonymousStoryViews * .07, 500_000)
                if (bulkReactions > 0) {
                    val emoji = listOf("❤️", "🔥", "😍", "👏")[rng.int(4)]
                    reactions[emoji] = (reactions[emoji] ?: 0L) + bulkReactions
                }
                val bulkProfileVisits = timing.events("story-visit:${story.id}", anonymousStoryViews * .014, 500_000)
                visits = visits.plusStat(bulkProfileVisits.toLong())
                insights = insights.copy(
                    impressions = insights.impressions + anonymousStoryViews,
                    likes = insights.likes + bulkReactions,
                    forward = insights.forward + (anonymousStoryViews * .62).toLong(),
                    exited = insights.exited + (anonymousStoryViews * .15).toLong(),
                    nextStory = insights.nextStory + (anonymousStoryViews * .15).toLong(),
                    back = insights.back + (anonymousStoryViews * .08).toLong(),
                    profileVisits = insights.profileVisits + bulkProfileVisits,
                    shares = insights.shares + timing.events(
                        "story-share:${story.id}",
                        anonymousStoryViews * .009,
                        500_000
                    ),
                    stickerTaps = insights.stickerTaps +
                            if (story.overlay.sticker.isBlank()) 0
                            else timing.events("story-sticker:${story.id}", anonymousStoryViews * .035, 500_000)
                )
            }
            story.copy(
                views = story.views.plusStat(count.toLong()),
                viewers = sample,
                insights = insights,
                reactionCounts = reactions
            )
        }.toMutableList()
        if (hasContent && elapsed > 180) repeat(
            timing.events(
                "discover-follow",
                (g.followersPerHour * traffic * ambient * (1.0 + sqrt(followerCount / 900.0)))
                    .coerceAtMost(50.0)
            )
        ) { schedule(InteractionKind.FOLLOW, person()) }
        val due = pending.filter { it.due <= elapsed }
        pending = pending.filter { it.due > elapsed }.toMutableList()
        for (item in due) {
            val pIndex = posts.indexOfFirst { it.id == item.postId }
            val tIndex = stories.indexOfFirst { it.id == item.storyId }
            when (item.kind) {
                InteractionKind.LIKE, InteractionKind.COMMENT -> {
                    if (pIndex < 0 || posts[pIndex].frozen) continue
                    val p = posts[pIndex]
                    if (item.kind == InteractionKind.LIKE) {
                        if (item.person.id in p.likerIds) continue
                        posts[pIndex] = p.copy(
                            likerIds = p.likerIds + item.person.id,
                            likes = p.likes.plusStat(1),
                            sampledLikers = (listOf(item.person) + p.sampledLikers).distinctBy { it.id }.take(180)
                        )
                        addActivity(
                            item.kind,
                            item.person,
                            item.id,
                            postId = p.id,
                            text = if (p.media.kind == MediaKind.REEL) "reel" else "photo"
                        )
                    } else {
                        val generated = CommentGenerator.generate(
                            rng.seed,
                            now,
                            item.id,
                            CommentContext(p.caption, p.location, p.media.kind),
                            recentTexts = p.comments.map { it.text }
                        ).copy(person = item.person)
                        rng.next()
                        val root = p.comments.filter { it.parentId == null }.let { it.getOrNull(rng.int(it.size)) }
                        val comment = generated.copy(parentId = if (root != null && rng.next() < .12) root.id else null)
                        posts[pIndex] = p.copy(
                            commentCount = p.commentCount.plusStat(1),
                            comments = (p.comments + comment).threadWindow()
                        )
                        addActivity(item.kind, item.person, item.id, postId = p.id, text = comment.text)
                    }
                }

                InteractionKind.FOLLOW -> {
                    if (!followerIds.add(item.person.id)) continue
                    followers = followers.plusStat(1)
                    addActivity(item.kind, item.person.copy(followsYou = true), item.id)
                }

                InteractionKind.STORY_REPLY -> {
                    if (tIndex < 0 || stories[tIndex].expiresAt <= now) continue
                    val story = stories[tIndex]
                    val message = listOf(
                        "where is this?",
                        "love this 🤍",
                        "the light is so good",
                        "okay this is a vibe",
                        "need to go here",
                        "🥹🫶"
                    )[rng.int(6)]
                    stories[tIndex] = story.copy(insights = story.insights.copy(replies = story.insights.replies + 1))
                    addActivity(item.kind, item.person, item.id, storyId = story.id, text = message)
                }

                InteractionKind.STORY_REACTION -> Unit
            }
        }
        var history = s.liveHistory
        if (live != null) {
            val previousFollowers = live.gainedFollowers.map { it.id }.toSet()
            live = LivestreamEngine.step(live, dt, settings, now, followers)
            live.gainedFollowers.filter { it.id !in previousFollowers }.forEach { actor ->
                if (followerIds.add(actor.id)) {
                    followers = followers.plusStat(1)
                    addActivity(
                        InteractionKind.FOLLOW,
                        actor.copy(followsYou = true),
                        "live-follow-${live.id}-${actor.id}"
                    )
                }
            }
            if (live.endedAt != null) {
                history = (listOf(live) + history).take(30); live = null
            }
        }
        return s.copy(
            profile = s.profile.copy(followers = followers, visits = visits),
            posts = posts,
            stories = stories,
            people = people,
            activity = activity,
            activeLive = live,
            liveHistory = history,
            events = events,
            engine = s.engine.copy(
                seed = rng.seed,
                elapsedSeconds = elapsed,
                clocks = timing.clocks,
                pending = pending,
                nextActor = nextActor,
                followerIds = followerIds,
                phaseMultiplier = phase,
                phaseUntil = phaseUntil,
                events = viral,
                eventCooldown = cooldown,
                ambientMultiplier = ambient,
                ambientUntil = ambientUntil,
                storySurgeUntil = storySurgeUntil,
                commentFloodUntil = floodUntil
            )
        )
    }
}
