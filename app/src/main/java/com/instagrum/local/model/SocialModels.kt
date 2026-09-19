package com.instagrum.local.model

import kotlinx.serialization.Serializable

@Serializable
enum class InteractionKind { LIKE, FOLLOW, COMMENT, STORY_REACTION, STORY_REPLY }

@Serializable
data class SocialActivity(
    val id: String,
    val kind: InteractionKind,
    val person: FakePerson,
    val createdAt: Long,
    val postId: String? = null,
    val storyId: String? = null,
    val text: String = "",
    val read: Boolean = false,
)

@Serializable
data class PendingInteraction(
    val id: String, val due: Double, val kind: InteractionKind, val person: FakePerson,
    val postId: String? = null, val storyId: String? = null,
)

@Serializable
data class StoryOverlay(
    val text: String = "", val sticker: String = "", val x: Float = .5f, val y: Float = .45f,
    val textColor: Long = 0xFFFFFFFF, val closeFriends: Boolean = false,
)

@Serializable
data class StoryVisit(val person: FakePerson, val at: Long, val reaction: String = "")

@Serializable
data class StoryInsights(
    val impressions: Long = 0, val likes: Long = 0, val shares: Long = 0,
    val replies: Long = 0, val profileVisits: Long = 0, val follows: Long = 0,
    val stickerTaps: Long = 0, val back: Long = 0, val forward: Long = 0,
    val nextStory: Long = 0, val exited: Long = 0,
)

@Serializable
data class LiveViewer(val person: FakePerson, val joinedAt: Double, val leavesAt: Double, val talkative: Boolean)

fun SocialActivity.message(): String = when (kind) {
    InteractionKind.LIKE -> "liked your ${if (text == "reel") "reel" else "photo"}."
    InteractionKind.FOLLOW -> "started following you."
    InteractionKind.COMMENT -> "commented: $text"
    InteractionKind.STORY_REACTION -> "reacted $text to your story."
    InteractionKind.STORY_REPLY -> "replied to your story: $text"
}
