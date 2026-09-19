package com.instagrum.local.model

sealed interface Action {
    data class Navigate(val screen: String = "main", val tab: String = "home") : Action
    data class SwitchAccount(val id: String) : Action
    data object CreateAccount : Action
    data class OpenPost(val id: String?) : Action
    data class OpenStory(val id: String?, val progress: Float = 0f, val highlight: String? = null) : Action
    data class ViewStory(val id: String) : Action
    data class StoryProgress(val progress: Float) : Action
    data class GridPosition(val index: Int, val offset: Int, val tab: Int) : Action
    data class EditProfile(val profile: Profile) : Action
    data class CompleteProfile(val profile: Profile, val keepContent: Boolean = true) : Action
    data class ChooseGrowth(val preset: GrowthPreset) : Action
    data class OpenActivity(val id: String) : Action
    data class SaveSettings(val settings: SimulationSettings) : Action
    data class SaveDraft(val draft: CreationDraft) : Action
    data class StoryStats(val id: String, val views: Long, val targetViews: Long) : Action
    data class SetSeed(val seed: Long) : Action
    data class CreatePost(
        val media: Media, val caption: String, val location: String,
        val likes: Long, val comments: Long, val views: Long, val viralPotential: Int
    ) : Action

    data class CreateStory(
        val media: Media,
        val caption: String,
        val targetViews: Long,
        val highlight: String,
        val overlay: StoryOverlay = StoryOverlay()
    ) : Action

    data class LikePost(val id: String, val onlyLike: Boolean = false) : Action
    data class SavePost(val id: String) : Action
    data class DeletePost(val id: String) : Action
    data class AddComment(val postId: String, val text: String, val parentId: String? = null) : Action
    data class LikeComment(val postId: String, val commentId: String) : Action
    data class StoryReply(val id: String, val text: String) : Action
    data class Highlight(val id: String, val name: String) : Action
    data class DeleteStory(val id: String) : Action
    data class Follow(val personId: String) : Action
    data class PostStats(
        val id: String,
        val likes: Long,
        val comments: Long,
        val views: Long,
        val frozen: Boolean,
        val potential: Int
    ) : Action

    data class AccountStats(val followers: Long, val following: Long, val visits: Long) : Action
    data class AddFollowers(val count: Long) : Action
    data class AddViews(val id: String, val count: Long) : Action
    data class Viral(val id: String, val strength: Double = 18.0) : Action
    data class EndViral(val id: String? = null) : Action
    data class StartLive(val config: LiveConfig) : Action
    data object StopLive : Action
    data object LiveLike : Action
    data object LiveHype : Action
    data class LiveComment(val text: String) : Action
    data object ReadEvents : Action
    data object Refresh : Action
    data object ResetStatistics : Action
}
