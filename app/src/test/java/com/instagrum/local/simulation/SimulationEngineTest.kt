package com.instagrum.local.simulation

import com.instagrum.local.data.InitialState
import com.instagrum.local.data.StateReducer
import com.instagrum.local.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SimulationEngineTest {
    private val now = 1_730_000_000_000L

    private fun owned(preset: GrowthPreset = GrowthPreset.NORMAL, now: Long = this.now) =
        InitialState.create(now).copy(
            profile = Profile("me", "Me"),
            profileCreated = true,
            settings = GrowthPresets.settings(preset)
        )

    private fun withContent(state: AppState): AppState {
        val post = Post(
            "p1", Media(artwork = 2), "hello", "", now, 0, 0, 0,
            publishedAtSimulation = state.engine.elapsedSeconds
        )
        val story = Story(
            "s1", Media(artwork = 1), "", now, now + 86_400_000, 0, Long.MAX_VALUE,
            publishedAtSimulation = state.engine.elapsedSeconds
        )
        return state.copy(posts = listOf(post), stories = listOf(story))
    }

    private fun run(state: AppState, seconds: Double): AppState =
        SimulationEngine.step(state, seconds, now + (seconds * 1000).toLong())

    @Test
    fun seededStepsAreReplayable() {
        val seed = withContent(owned())
        assertEquals(run(seed, 600.0), run(seed, 600.0))
    }

    @Test
    fun pausedAndFrozenDoNotMove() {
        val seed = withContent(owned())
        assertEquals(
            seed.copy(settings = seed.settings.copy(paused = true)),
            run(seed.copy(settings = seed.settings.copy(paused = true)), 600.0)
        )
        assertEquals(
            seed.copy(settings = seed.settings.copy(frozen = true)),
            run(seed.copy(settings = seed.settings.copy(frozen = true)), 600.0)
        )
    }

    @Test
    fun quietPresetNeverGrows() {
        val result = run(withContent(owned(GrowthPreset.DEAD)), 7200.0)
        assertEquals(0, result.profile.followers)
        assertEquals(0, result.posts.single().views)
        assertEquals(0, result.stories.single().views)
        assertTrue(result.activity.isEmpty())
    }

    @Test
    fun slowerPresetsGrowMoreSlowlyThanFasterOnes() {
        fun total(preset: GrowthPreset) = run(withContent(owned(preset)), 10800.0).let {
            it.posts.single().views + it.stories.single().views + it.profile.followers * 100
        }

        val slow = total(GrowthPreset.SLOW)
        val natural = total(GrowthPreset.NORMAL)
        val medium = total(GrowthPreset.MEDIUM)
        val fast = total(GrowthPreset.FAST)
        assertTrue("slow ($slow) should be below natural ($natural)", slow < natural)
        assertTrue("natural ($natural) should be below medium ($medium)", natural < medium)
        assertTrue("medium ($medium) should be below fast ($fast)", medium < fast)
    }

    @Test
    fun engagementArrivesThroughNamedActivityAndFollowersAreMarked() {
        val result = run(withContent(owned(GrowthPreset.EXTREME)), 3600.0)
        assertTrue(result.activity.isNotEmpty())
        assertTrue(result.activity.all { it.person.username.isNotBlank() })
        result.activity.filter { it.kind == InteractionKind.FOLLOW }.forEach {
            assertTrue(it.person.followsYou)
            assertTrue(result.people.find { p -> p.id == it.person.id }?.followsYou == true)
        }
        result.activity.filter { it.kind == InteractionKind.LIKE }.forEach { event ->
            val post = result.posts.find { it.id == event.postId }
            assertNotNull(post)
            assertTrue(event.person.id in post!!.likerIds)
        }
    }

    @Test
    fun sampledPeopleCannotLikeAPostOrFollowTwice() {
        val result = run(withContent(owned(GrowthPreset.EXTREME)), 7200.0)
        val post = result.posts.single()
        assertEquals(post.likerIds.size, post.likerIds.distinct().size)
        assertTrue(post.likerIds.size.toLong() <= post.likes)
        assertEquals(result.engine.followerIds.size, result.engine.followerIds.distinct().size)
        assertTrue(result.engine.followerIds.size.toLong() <= result.profile.followers)
    }

    @Test
    fun viralPostsCanReachAZeroFollowerAccountButStoriesCannot() {
        val zeroFollower = owned(GrowthPreset.VIRAL)
        val postResult = run(zeroFollower.copy(stories = emptyList(), posts = withContent(zeroFollower).posts), 120.0)
        assertTrue("recommendations should reach posts without followers", postResult.posts.single().views > 0)
        assertTrue("post engagement should arrive asynchronously", postResult.posts.single().likes > 0)

        val storyResult = run(withContent(zeroFollower).copy(posts = emptyList()), 3600.0)
        assertEquals("stories remain follower-network based", 0, storyResult.stories.single().views)
    }

    @Test
    fun storyReachScalesWithFollowers() {
        val small = run(withContent(owned(GrowthPreset.NORMAL)).copy(posts = emptyList()), 3600.0)
        val larger = run(
            withContent(
                owned(GrowthPreset.NORMAL).copy(
                    profile = Profile(
                        "me",
                        "Me",
                        followers = 1_000L
                    )
                )
            ).copy(posts = emptyList()),
            3600.0
        )
        assertEquals(0, small.stories.single().views)
        assertTrue("followers should create story reach", larger.stories.single().views > 0)
    }

    @Test
    fun viralMomentumExpandsDistributionAfterEarlyEngagement() {
        val result = run(withContent(owned(GrowthPreset.VIRAL)), 600.0)
        val post = result.posts.single()
        assertTrue("viral content should receive meaningful early reach", post.views >= 100)
        assertTrue("likes should be tied to actual viewers", post.likes > 0 && post.likes <= post.views)
        assertTrue("discovery should be able to convert viewers into followers", result.profile.followers > 0)
    }

    @Test
    fun tenMillionFollowerAccountScalesWithoutFixedResults() {
        val large = withContent(
            owned(GrowthPreset.VIRAL).copy(profile = Profile("me", "Me", followers = 10_000_000L))
        ).copy(settings = GrowthPresets.settings(GrowthPreset.VIRAL).copy(randomEvents = false))
        val small = withContent(
            owned(GrowthPreset.VIRAL).copy(profile = Profile("me", "Me", followers = 1_000L))
        ).copy(settings = GrowthPresets.settings(GrowthPreset.VIRAL).copy(randomEvents = false))
        val largeResult = run(large, 180.0)
        val smallResult = run(small, 180.0)
        val post = largeResult.posts.single()
        println("10M followers / 3m: ${post.views} views, ${post.likes} likes, ${post.commentCount} comments")
        assertTrue(
            "large audiences should create much larger reach",
            post.views > smallResult.posts.single().views * 100
        )
        assertTrue("likes must emerge from reached viewers", post.likes > 0 && post.likes < post.views)
        assertTrue("engagement should remain human-scale", post.likes.toDouble() / post.views in .02..0.35)
    }

    @Test
    fun generatedCrowdsHaveLargeIdentityAndCommentVariety() {
        val usernames = (0 until 50_000).map { CommentGenerator.person(it).username }
        assertTrue("viral crowds should rarely repeat usernames", usernames.distinct().size > 48_000)
        assertTrue(CommentGenerator.pool.size > 2_000)
    }

    @Test
    fun storyViewersAndReactionTotalsAreRecorded() {
        val result = run(
            withContent(owned(GrowthPreset.EXTREME).copy(profile = Profile("me", "Me", followers = 1_000L))),
            3600.0
        )
        val story = result.stories.single()
        assertTrue(story.viewers.size > 0)
        assertTrue(story.insights.impressions >= story.views)
        assertEquals(story.reactionCounts.values.sum(), story.insights.likes)
        assertTrue(
            story.viewers.count { it.reaction.isNotEmpty() } <= story.reactionCounts.values.sum().toInt()
        )
    }

    @Test
    fun liveViewerCountsFluctuateInsteadOfOnlyIncreasing() {
        var session = LiveSession("live", LiveConfig(durationMinutes = 5, maxViewers = 400), now)
        val settings = GrowthPresets.settings(GrowthPreset.MEDIUM)
        val history = mutableListOf<Int>()
        repeat(240) {
            session = LivestreamEngine.step(session, 1.0, settings, now + it * 1000L, accountFollowers = 1_000)
            history += session.viewers
        }
        assertTrue(history.max() > 0)
        assertTrue("viewer count should sometimes go down", history.zipWithNext().any { (a, b) -> b < a })
        assertTrue(history.all { it in 0..400 })
        assertTrue(session.viewers <= 400)
        assertTrue(session.chat.size <= 80)
    }

    @Test
    fun liveFollowersBecomeNamedFollowActivity() {
        var state = owned(GrowthPreset.EXTREME).copy(
            activeLive = LiveSession("live", LiveConfig(durationMinutes = 60, maxViewers = 600), now)
        )
        repeat(600) { state = run(state, 1.0) }
        assertTrue(state.liveHistory.isNotEmpty() || state.activeLive != null)
        val follows = state.activity.filter { it.kind == InteractionKind.FOLLOW }
        assertTrue("follow activity cannot exceed follower count", follows.size.toLong() <= state.profile.followers)
        assertEquals(
            "stored follower ids must match the follower counter",
            state.engine.followerIds.size.toLong(),
            state.profile.followers
        )
    }

    @Test
    fun commentsAvoidRecentTextWhenPossible() {
        val one = CommentGenerator.generate(2, now, "1")
        val two = CommentGenerator.generate(2, now, "2", recentTexts = listOf(one.text))
        assertNotEquals(one.text, two.text)
        assertTrue(CommentGenerator.pool.size > 200)
    }

    @Test
    fun storiesReachTheFollowerNetworkAtEveryAccountSize() {
        // Regression: a 300-follower account used to get zero story views because
        // reach was capped by the preset's hourly story budget.
        val small = run(
            withContent(owned(GrowthPreset.NORMAL).copy(profile = Profile("me", "Me", followers = 300L)))
                .copy(posts = emptyList()),
            3600.0
        )
        assertTrue("300 followers must produce story views", small.stories.single().views > 0)

        val large = run(
            withContent(owned(GrowthPreset.NORMAL).copy(profile = Profile("me", "Me", followers = 1_000_000L)))
                .copy(posts = emptyList()),
            3600.0
        )
        assertTrue(
            "story reach must scale with the audience",
            large.stories.single().views > small.stories.single().views * 50
        )
        assertTrue(
            "insights must count every viewer",
            large.stories.single().insights.impressions >= large.stories.single().views
        )
    }

    @Test
    fun liveAudienceScalesWithFollowersAndPace() {
        fun peak(followers: Long, preset: GrowthPreset): Int {
            var live = LiveSession("live-$followers-$preset", LiveConfig(durationMinutes = 60), now)
            val settings = GrowthPresets.settings(preset)
            repeat(900) {
                live = LivestreamEngine.step(live, 1.0, settings, now + it * 1000L, accountFollowers = followers)
            }
            return live.peakViewers
        }

        val smallRoom = peak(1_000L, GrowthPreset.NORMAL)
        val bigRoom = peak(1_000_000L, GrowthPreset.NORMAL)
        val hugeRoom = peak(10_000_000L, GrowthPreset.NORMAL)
        assertTrue("a million followers should fill a large room ($bigRoom)", bigRoom > smallRoom * 50)
        assertTrue("ten million should go far beyond that ($hugeRoom)", hugeRoom > bigRoom * 5)
        assertTrue("celebrity pace must outdraw natural pace", peak(1_000_000L, GrowthPreset.CELEBRITY) > bigRoom * 3)
    }

    @Test
    fun changingPaceAppliesToContentThatAlreadyExists() {
        val seeded = run(withContent(owned(GrowthPreset.SLOW)), 600.0)
        val switched = StateReducer.reduce(seeded, Action.ChooseGrowth(GrowthPreset.CELEBRITY), now, "pace")
        assertTrue("stale hazard clocks must be cleared", switched.engine.clocks.isEmpty())
        assertEquals(GrowthPreset.CELEBRITY, switched.settings.preset)

        val before = run(seeded, 300.0).posts.single().views - seeded.posts.single().views
        val after = run(switched, 300.0).posts.single().views - switched.posts.single().views
        assertTrue("an existing post must speed up after a pace change ($before -> $after)", after > before * 10)
    }

    @Test
    fun serializationRoundTripPreservesSnapshot() {
        val state = withContent(owned())
        val json = Json { encodeDefaults = true }
        assertEquals(state, json.decodeFromString<AppState>(json.encodeToString(state)))
    }
}
