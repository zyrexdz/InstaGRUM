package com.instagrum.local.model

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatar: String = "",
    val avatarArtwork: Int = 8,
    val followers: Long = 0,
    val following: Long = 0,
    val visits: Long = 0,
    val verified: Boolean = false,
)

@Serializable
enum class MediaKind { IMAGE, VIDEO, REEL }

/** Empty paths use the bundled, entirely offline artwork indicated by artwork. */
@Serializable
data class Media(val path: String = "", val kind: MediaKind = MediaKind.IMAGE, val artwork: Int = 0)

@Serializable
data class FakePerson(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String = "",
    val colorIndex: Int = 0,
    val verified: Boolean = false,
    val followed: Boolean = false,
    val followsYou: Boolean = false,
)

@Serializable
data class Comment(
    val id: String,
    val person: FakePerson,
    val text: String,
    val createdAt: Long,
    val likes: Long = 0,
    val liked: Boolean = false,
    val parentId: String? = null,
    val own: Boolean = false,
)

@Serializable
data class Post(
    val id: String,
    val media: Media,
    val caption: String,
    val location: String = "",
    val createdAt: Long,
    val likes: Long = 0,
    val commentCount: Long = 0,
    val views: Long = 0,
    val comments: List<Comment> = emptyList(),
    val liked: Boolean = false,
    val saved: Boolean = false,
    val viralPotential: Int = 35,
    val frozen: Boolean = false,
    val viralUntil: Double = 0.0,
    val publishedAtSimulation: Double = 0.0,
    val sampledLikers: List<FakePerson> = emptyList(),
    val likerIds: Set<String> = emptySet(),
)

@Serializable
data class Story(
    val id: String,
    val media: Media,
    val caption: String = "",
    val createdAt: Long,
    val expiresAt: Long,
    val views: Long = 0,
    val targetViews: Long = 1500,
    val highlight: String = "",
    val replies: List<String> = emptyList(),
    val seen: Boolean = false,
    val publishedAtSimulation: Double = 0.0,
    val viewers: List<StoryVisit> = emptyList(),
    val insights: StoryInsights = StoryInsights(),
    val reactionCounts: Map<String, Long> = emptyMap(),
    val overlay: StoryOverlay = StoryOverlay(),
)

@Serializable
enum class GrowthPreset { DEAD, VERY_SLOW, SLOW, NORMAL, MEDIUM, FAST, VIRAL, EXTREME, CELEBRITY, CUSTOM }

@Serializable
data class GrowthProfile(
    val followersPerHour: Double = 120.0,
    val likesPerHour: Double = 400.0,
    val commentsPerHour: Double = 24.0,
    val storyViewsPerHour: Double = 500.0,
    val postViewsPerHour: Double = 6000.0,
    val reelViewsPerHour: Double = 12000.0,
    val profileVisitsPerHour: Double = 90.0,
)

@Serializable
data class EngagementProfile(
    val multiplier: Double = 1.0,
    val likeRate: Double = 0.062,
    val commentRate: Double = 0.0048,
    val visitRate: Double = 0.0037,
    val followRate: Double = 0.0012,
)

@Serializable
data class SimulationSettings(
    val preset: GrowthPreset = GrowthPreset.NORMAL,
    val growth: GrowthProfile = GrowthProfile(),
    val engagement: EngagementProfile = EngagementProfile(),
    val speed: Int = 1,
    val paused: Boolean = false,
    val frozen: Boolean = false,
    val randomEvents: Boolean = true,
    val viralProbabilityPerHour: Double = 0.18,
    val liveViewerMultiplier: Double = 1.0,
    val liveCommentMultiplier: Double = 1.0,
    val darkMode: Boolean = true,
    val enabledEvents: Set<EventKind> = EventKind.entries.filter { it != EventKind.MANUAL }.toSet(),
    val notificationsEnabled: Boolean = true,
    val backgroundActivity: Boolean = true,
)

@Serializable
enum class EventKind { VIRAL, REEL_SPIKE, STORY_SPIKE, REPOST, FOLLOWERS, ENGAGEMENT_DROP, LIVE_SPIKE, RAID, COMMENT_FLOOD, MANUAL }

@Serializable
data class GrowthEvent(
    val id: String,
    val kind: EventKind,
    val title: String,
    val detail: String,
    val createdAt: Long,
    val postId: String? = null,
    val read: Boolean = false,
)

@Serializable
data class ViralEvent(val postId: String, val startedAt: Double, val durationSeconds: Double, val strength: Double)

@Serializable
data class EngineState(
    val seed: Long = 814729L,
    val elapsedSeconds: Double = 0.0,
    val carry: Map<String, Double> = emptyMap(),
    val events: List<ViralEvent> = emptyList(),
    val eventCooldown: Double = 0.0,
    val ambientMultiplier: Double = 1.0,
    val ambientUntil: Double = 0.0,
    val storySurgeUntil: Double = 0.0,
    val commentFloodUntil: Double = 0.0,
    val clocks: Map<String, Double> = emptyMap(),
    val pending: List<PendingInteraction> = emptyList(),
    val phaseUntil: Double = 0.0,
    val phaseMultiplier: Double = 1.0,
    val nextActor: Int = 0,
    val followerIds: Set<String> = emptySet(),
)

@Serializable
data class LiveConfig(
    val title: String = "A little catch-up ✨",
    val thumbnail: Media = Media(artwork = 4),
    val startingViewers: Int = 0,
    val minViewers: Int = 0,
    val maxViewers: Int = 50_000_000,
    val growthPerMinute: Double = 65.0,
    val declinePerMinute: Double = 35.0,
    val commentsPerMinute: Double = 35.0,
    val likesPerMinute: Double = 160.0,
    val durationMinutes: Int = 30,
    val viralIntensity: Double = 0.35,
)

@Serializable
data class LiveSession(
    val id: String,
    val config: LiveConfig,
    val startedAt: Long,
    val elapsedSeconds: Double = 0.0,
    val viewers: Int = 0,
    val peakViewers: Int = 0,
    val likes: Long = 0,
    val totalComments: Long = 0,
    val newFollowers: Long = 0,
    val chat: List<Comment> = emptyList(),
    val endedAt: Long? = null,
    val seed: Long = 73492L,
    val carry: Map<String, Double> = emptyMap(),
    val surgeUntil: Double = 0.0,
    val audience: List<LiveViewer> = emptyList(),
    val arrivalClock: Double = -1.0,
    val chatClock: Double = -1.0,
    val likeClock: Double = -1.0,
    val phaseUntil: Double = 0.0,
    val phaseMultiplier: Double = 1.0,
    val initialized: Boolean = false,
    val gainedFollowers: List<FakePerson> = emptyList(),
)

@Serializable
data class UiSession(
    val tab: String = "home",
    val openPostId: String? = null,
    val openStoryId: String? = null,
    val storyProgress: Float = 0f,
    val screen: String = "main",
    val gridTab: Int = 0,
    val gridIndex: Int = 0,
    val gridOffset: Int = 0,
    val storyHighlight: String = "",
)

@Serializable
data class CreationDraft(
    val mode: Int = 0,
    val media: Media = Media(),
    val caption: String = "",
    val location: String = "",
    val likes: String = "0",
    val comments: String = "0",
    val views: String = "0",
    val potential: Int = 35,
    val highlight: String = "",
    val liveConfig: LiveConfig = LiveConfig(),
    val stage: String = "camera",
    val overlay: StoryOverlay = StoryOverlay(),
)

@Serializable
data class AccountSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String = "",
    val avatarArtwork: Int = 8,
)

@Serializable
data class AppState(
    val schemaVersion: Int = 1,
    val activeAccountId: String = "default",
    val accounts: List<AccountSummary> = emptyList(),
    val profile: Profile = Profile(),
    val posts: List<Post> = emptyList(),
    val stories: List<Story> = emptyList(),
    val people: List<FakePerson> = emptyList(),
    val settings: SimulationSettings = SimulationSettings(),
    val engine: EngineState = EngineState(),
    val events: List<GrowthEvent> = emptyList(),
    val activeLive: LiveSession? = null,
    val liveHistory: List<LiveSession> = emptyList(),
    val session: UiSession = UiSession(),
    val lastSavedAt: Long = 0L,
    val draft: CreationDraft = CreationDraft(),
    val profileCreated: Boolean = false,
    val activity: List<SocialActivity> = emptyList(),
    val acknowledgedFollowers: Long = 0L,
    val acknowledgedLikes: Long = 0L,
    val lastSimulationAt: Long = 0L,
)
