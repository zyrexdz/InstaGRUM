package com.instagrum.local.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.instagrum.local.data.InitialState
import com.instagrum.local.data.StateReducer
import com.instagrum.local.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class AppFlowsTest {
    @get:Rule
    val compose = createComposeRule()
    private val now = System.currentTimeMillis()
    private val state = mutableStateOf(InitialState.create(now))
    private var sequence = 0

    private fun dispatch(action: Action) {
        state.value = StateReducer.reduce(state.value, action, now, "ui-${sequence++}")
    }

    private fun launch() {
        compose.setContent {
            InstaTheme(state.value.settings.darkMode) {
                MediaProvider { InstaApp(state.value, ::dispatch) }
            }
        }
    }

    private fun completeOnboarding() {
        compose.onNodeWithText("Make it yours.").assertIsDisplayed()
        compose.onNodeWithTag("setupUsername").performTextInput("my.world")
        compose.onNodeWithTag("setupName").performTextInput("My Name")
        compose.onNodeWithTag("completeProfile").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertTrue(state.value.profileCreated) }
    }

    @Test
    fun onboardingThenCreatePostThenLikeAndComment() {
        launch()
        completeOnboarding()
        compose.onNodeWithText("my.world").assertIsDisplayed()
        compose.onNodeWithContentDescription("create").performClick()
        compose.onNodeWithText("POST").performClick()
        // Gallery is an Android document picker; use a bundled cover to finish the flow offline.
        compose.runOnIdle {
            dispatch(
                Action.CreatePost(
                    Media(artwork = 3), "A quiet morning", "", 0, 0, 0, 35
                )
            )
        }
        compose.onNodeWithTag("postMedia").assertIsDisplayed()
        compose.onNodeWithContentDescription("Like post").performClick()
        compose.runOnIdle { assertTrue(state.value.posts.first().liked) }
        compose.onNodeWithContentDescription("Open comments").performClick()
        compose.onNodeWithTag("commentInput").performTextInput("From my phone")
        compose.onNodeWithContentDescription("Post comment").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(1, state.value.posts.first().commentCount)
            assertEquals("From my phone", state.value.posts.first().comments.single().text)
        }
    }

    @Test
    fun chooseSlowGrowthPaceFromSettings() {
        launch()
        completeOnboarding()
        compose.onNodeWithContentDescription("Simulation settings").performClick()
        compose.onNodeWithTag("pace-SLOW").performClick()
        compose.runOnIdle { assertEquals(GrowthPreset.SLOW, state.value.settings.preset) }
        compose.onNodeWithTag("pace-FAST").performClick()
        compose.runOnIdle { assertEquals(GrowthPreset.FAST, state.value.settings.preset) }
    }

    @Test
    fun storyOpensInsightsWithViewerList() {
        launch()
        completeOnboarding()
        // Keep the story's 7-second playback paused during UI assertions.
        compose.mainClock.autoAdvance = false
        // Stories are follower-network content; give this UI fixture an audience.
        state.value = state.value.copy(profile = state.value.profile.copy(followers = 1_000))
        state.value = StateReducer.reduce(
            state.value,
            Action.CreateStory(Media(artwork = 2), "", Long.MAX_VALUE, "", StoryOverlay(text = "hey")),
            now,
            "ui-${sequence++}"
        )
        compose.waitForIdle()
        // Complete the short navigation animation, but not the story's 7-second timer.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("storyViewer").assertExists()
        compose.onNodeWithContentDescription("Story insights").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag("storyInsights").assertIsDisplayed()
        compose.onNodeWithText("Viewers").assertExists()
        compose.onNodeWithContentDescription("Insights tab", useUnmergedTree = true).assertExists()
    }

    @Test
    fun liveStartsFromCreatorAndShowsEmptyRoomMessage() {
        launch()
        completeOnboarding()
        compose.onNodeWithContentDescription("create").performClick()
        compose.onNodeWithText("LIVE").performClick()
        compose.runOnIdle {
            dispatch(
                Action.StartLive(
                    LiveConfig(title = "Test live", durationMinutes = 60)
                )
            )
        }
        compose.onNodeWithText("LIVE").assertExists()
        compose.runOnIdle { assertNotNull(state.value.activeLive) }
        compose.onNodeWithText("Give people a moment to join.", substring = true).assertExists()
    }
}
