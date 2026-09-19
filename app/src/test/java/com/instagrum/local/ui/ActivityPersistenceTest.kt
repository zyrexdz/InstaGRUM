package com.instagrum.local.ui

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.instagrum.local.AppViewModel
import com.instagrum.local.MainActivity
import com.instagrum.local.data.SnapshotStore
import com.instagrum.local.model.Action
import com.instagrum.local.model.CreationDraft
import com.instagrum.local.data.InitialState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class ActivityPersistenceTest {
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15)
        }
        assertEquals(true, condition())
    }

    @Test
    fun activityLaunchSaveStopAndColdReopenRestoresState() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        // Accounts each own a snapshot directory; "default" is the migrated one.
        val store = SnapshotStore(java.io.File(application.filesDir, "accounts/default"))
        store.save(
            InitialState.create(System.currentTimeMillis()).copy(
                profile = com.instagrum.local.model.Profile("first.user", "First"),
                profileCreated = true,
                settings = com.instagrum.local.model.SimulationSettings(paused = true)
            )
        )
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val first = ViewModelProvider(controller.get())[AppViewModel::class.java]
        await { first.state.value != null }
        first.dispatch(Action.EditProfile(first.state.value!!.profile.copy(username = "reopen.local")))
        first.dispatch(Action.SaveDraft(CreationDraft(caption = "draft survives closing")))
        first.dispatch(Action.Navigate("settings"))
        await { store.load()?.session?.screen == "settings" && store.load()?.profile?.username == "reopen.local" && store.load()?.draft?.caption == "draft survives closing" }
        controller.pause().stop().destroy()

        val nextController = Robolectric.buildActivity(MainActivity::class.java).setup()
        val second = ViewModelProvider(nextController.get())[AppViewModel::class.java]
        await { second.state.value != null }
        assertEquals("reopen.local", second.state.value!!.profile.username)
        assertEquals("settings", second.state.value!!.session.screen)
        assertEquals("draft survives closing", second.state.value!!.draft.caption)
        nextController.pause().stop().destroy()
    }
}
