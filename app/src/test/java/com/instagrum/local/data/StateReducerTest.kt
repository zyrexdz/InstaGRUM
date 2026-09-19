package com.instagrum.local.data

import com.instagrum.local.model.*
import com.instagrum.local.simulation.GrowthPresets
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class StateReducerTest {
    private val now = 1_730_000_000_000L

    @Test
    fun onboardingCreatesEmptyOwnedProfile() {
        val initial = InitialState.create(now)
        assertFalse(initial.profileCreated)
        assertEquals(0, initial.profile.followers)
        val completed = StateReducer.reduce(
            initial,
            Action.CompleteProfile(Profile("sam.wilder", "Sam", "hello")),
            now,
            "setup"
        )
        assertTrue(completed.profileCreated)
        assertEquals("sam.wilder", completed.profile.username)
        assertEquals(0, completed.profile.followers)
        assertEquals(0, completed.profile.following)
        assertEquals(GrowthPreset.NORMAL, completed.settings.preset)
    }

    @Test
    fun choosingAGrowthPresetKeepsTheRestOfTheState() {
        val state = InitialState.create(now).copy(
            profile = Profile("me", "Me"), profileCreated = true
        )
        val slow = StateReducer.reduce(state, Action.ChooseGrowth(GrowthPreset.SLOW), now, "pace")
        assertEquals(GrowthPreset.SLOW, slow.settings.preset)
        val fast = StateReducer.reduce(slow, Action.ChooseGrowth(GrowthPreset.FAST), now, "pace2")
        assertEquals(GrowthPreset.FAST, fast.settings.preset)
        assertEquals(GrowthPresets.profile(GrowthPreset.FAST), fast.settings.growth)
    }

    @Test
    fun creatingAPostAndStoryStartsAtZeroAndOpensThem() {
        val state = InitialState.create(now).copy(
            profile = Profile("me", "Me"), profileCreated = true
        )
        val posted = StateReducer.reduce(
            state,
            Action.CreatePost(Media(artwork = 3), "first", "", 0, 0, 0, 35),
            now,
            "post-1"
        )
        assertEquals("post-1", posted.session.openPostId)
        assertEquals(0, posted.posts.single().likes)
        assertEquals(0, posted.posts.single().views)

        val story = StateReducer.reduce(
            posted,
            Action.CreateStory(Media(artwork = 1), "hey", Long.MAX_VALUE, "", StoryOverlay(text = "hi")),
            now,
            "story-1"
        )
        assertEquals("story-1", story.session.openStoryId)
        assertEquals(0, story.stories.single().views)
    }

    @Test
    fun ownCommentsNestAndIncrement() {
        var state = InitialState.create(now).copy(profile = Profile("me", "Me"), profileCreated = true)
        state = StateReducer.reduce(state, Action.CreatePost(Media(), "x", "", 0, 0, 0, 0), now, "p")
        val withComment = StateReducer.reduce(state, Action.AddComment("p", "first comment"), now, "c1")
        assertEquals(1, withComment.posts.single().commentCount)
        val parent = withComment.posts.single().comments.single().id
        val nested = StateReducer.reduce(withComment, Action.AddComment("p", "reply", parent), now, "c2")
        assertEquals(2, nested.posts.single().commentCount)
        assertEquals(parent, nested.posts.single().comments.last().parentId)
    }

    @Test
    fun openingActivityMarksItReadAndNavigatesToContent() {
        val person = FakePerson("person-1", "jordan.daily", "Jordan")
        val post = Post("p", Media(), "hi", "", now)
        val event = SocialActivity("a1", InteractionKind.LIKE, person, now, postId = "p")
        val state = InitialState.create(now).copy(
            profile = Profile("me", "Me"), profileCreated = true,
            posts = listOf(post), activity = listOf(event)
        )
        val opened = StateReducer.reduce(state, Action.OpenActivity("a1"), now, "nav")
        assertTrue(opened.activity.single().read)
        assertEquals("p", opened.session.openPostId)
    }

    @Test
    fun settingsClampSimulationSpeed() {
        val state = InitialState.create(now)
        val updated = StateReducer.reduce(state, Action.SaveSettings(state.settings.copy(speed = 999)), now, "settings")
        assertEquals(100, updated.settings.speed)
    }

    @Test
    fun snapshotRecoversFromDamagedPrimary() {
        val directory = Files.createTempDirectory("instagrum-test").toFile()
        val store = SnapshotStore(directory)
        val state = InitialState.create(now)
        store.save(state)
        store.save(state.copy(profile = state.profile.copy(username = "second")))
        java.io.File(directory, "instagrum-local-state.json").writeText("damaged")
        assertEquals("", store.load()?.profile?.username)
        directory.deleteRecursively()
    }

    @Test
    fun startAndStopLiveCreatesHistory() {
        val state = InitialState.create(now)
        val started = StateReducer.reduce(state, Action.StartLive(LiveConfig(durationMinutes = 5)), now, "live")
        assertNotNull(started.activeLive)
        val stopped = StateReducer.reduce(started, Action.StopLive, now + 1000, "stop")
        assertNull(stopped.activeLive)
        assertEquals(1, stopped.liveHistory.size)
    }
}
