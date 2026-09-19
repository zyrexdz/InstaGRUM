package com.instagrum.local.data

import com.instagrum.local.model.*
import com.instagrum.local.simulation.CommentGenerator
import com.instagrum.local.simulation.GrowthPresets

object StateReducer {
    private const val LIMIT = 9_000_000_000_000L
    private fun Long.stat() = coerceIn(0, LIMIT)
    fun reduce(s: AppState, action: Action, now: Long, id: String): AppState {
        fun posts(transform: (Post) -> Post) = s.copy(posts = s.posts.map(transform))
        fun ownComment(text: String, parent: String? = null) = Comment(
            id,
            FakePerson(
                "self",
                s.profile.username,
                s.profile.displayName,
                s.profile.avatar,
                verified = s.profile.verified
            ),
            text.trim().take(2000),
            now,
            parentId = parent,
            own = true
        )
        return when (action) {
            is Action.SwitchAccount, Action.CreateAccount -> s
            is Action.CompleteProfile -> {
                val base = if (action.keepContent) s else InitialState.create(now)
                base.copy(
                    profile = action.profile.copy(
                        username = action.profile.username.trim().lowercase()
                            .filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(30),
                        displayName = action.profile.displayName.trim().take(80),
                        followers = 0,
                        following = 0,
                        visits = 0
                    ),
                    profileCreated = true,
                    activity = emptyList(),
                    events = emptyList(),
                    people = emptyList(),
                    engine = EngineState(seed = now),
                    lastSimulationAt = now,
                    session = UiSession(),
                    draft = CreationDraft()
                )
            }

            is Action.ChooseGrowth -> s.copy(
                settings = GrowthPresets.settings(action.preset, s.settings),

                engine = s.engine.copy(
                    clocks = emptyMap(),
                    pending = emptyList(),
                    phaseUntil = 0.0,
                    events = emptyList(),
                    eventCooldown = 0.0,
                    ambientMultiplier = 1.0,
                    ambientUntil = 0.0
                ),
                activeLive = s.activeLive?.copy(carry = emptyMap(), phaseUntil = 0.0)
            )

            is Action.OpenActivity -> {
                val item = s.activity.find { it.id == action.id }
                s.copy(
                    activity = s.activity.map { if (it.id == action.id) it.copy(read = true) else it },
                    session = UiSession(
                        tab = "notifications",
                        openPostId = item?.postId?.takeIf { id -> s.posts.any { it.id == id } },
                        openStoryId = item?.storyId?.takeIf { id -> s.stories.any { it.id == id } })
                )
            }

            is Action.Navigate -> s.copy(
                session = s.session.copy(
                    screen = action.screen,
                    tab = action.tab,
                    openPostId = null,
                    openStoryId = null
                )
            )

            is Action.OpenPost -> s.copy(session = s.session.copy(openPostId = action.id))
            is Action.OpenStory -> s.copy(
                session = s.session.copy(
                    openStoryId = action.id,
                    storyProgress = action.progress,
                    storyHighlight = action.highlight.orEmpty(),
                ), stories = s.stories.map { if (it.id == action.id) it.copy(seen = true) else it })

            is Action.ViewStory -> s.copy(stories = s.stories.map { story ->
                if (story.id == action.id) story.copy(
                    seen = true,
                    views = if (story.expiresAt > now && story.views < story.targetViews) (story.views + 1).coerceAtMost(
                        story.targetViews
                    ) else story.views
                ) else story
            })

            is Action.StoryProgress -> s.copy(
                session = s.session.copy(
                    storyProgress = action.progress.coerceIn(
                        0f,
                        1f
                    )
                )
            )

            is Action.GridPosition -> s.copy(
                session = s.session.copy(
                    gridIndex = action.index,
                    gridOffset = action.offset,
                    gridTab = action.tab
                )
            )

            is Action.EditProfile -> s.copy(
                profile = action.profile.copy(
                    username = action.profile.username.trim().filter { it.isLetterOrDigit() || it == '_' || it == '.' }
                        .take(30).ifBlank { s.profile.username },
                    displayName = action.profile.displayName.take(80),
                    bio = action.profile.bio.take(300),
                    followers = s.profile.followers, following = s.profile.following, visits = s.profile.visits,
                ), session = s.session.copy(screen = "main")
            )

            is Action.SaveSettings -> s.copy(settings = action.settings.validated())

            is Action.SaveDraft -> s.copy(draft = action.draft)
            is Action.StoryStats -> s.copy(stories = s.stories.map { story ->
                if (story.id == action.id) story.copy(
                    views = action.views.coerceIn(0, action.targetViews.coerceAtLeast(0)),
                    targetViews = action.targetViews.coerceAtLeast(0)
                ) else story
            })

            is Action.SetSeed -> s.copy(engine = s.engine.copy(seed = if (action.seed == 0L) 814729L else action.seed))

            is Action.CreatePost -> {
                val sample = (0 until action.comments.coerceIn(0, 60).toInt()).map {
                    CommentGenerator.generate(
                        now + it * 817L,
                        now,
                        "$id-comment-$it"
                    )
                }
                val post = Post(
                    id,
                    action.media,
                    action.caption.take(2200),
                    action.location.take(100),
                    now,
                    action.likes.stat(),
                    action.comments.stat(),
                    action.views.stat(),
                    sample,
                    viralPotential = action.viralPotential.coerceIn(0, 100),
                    publishedAtSimulation = s.engine.elapsedSeconds
                )
                s.copy(posts = listOf(post) + s.posts, session = UiSession(openPostId = id), draft = CreationDraft())
            }

            is Action.CreateStory -> s.copy(
                stories = s.stories + Story(
                    id,
                    action.media,
                    action.caption.take(500),
                    now,
                    now + 86400000,
                    targetViews = action.targetViews.stat(),
                    highlight = action.highlight.trim().take(30),
                    publishedAtSimulation = s.engine.elapsedSeconds,
                    overlay = action.overlay,
                ), session = UiSession(openStoryId = id), draft = CreationDraft(mode = 1)
            )

            is Action.LikePost -> posts { p ->
                if (p.id != action.id || (action.onlyLike && p.liked)) p else p.copy(
                    liked = !p.liked,
                    likes = (p.likes + if (p.liked) -1 else 1).stat()
                )
            }

            is Action.SavePost -> posts { if (it.id == action.id) it.copy(saved = !it.saved) else it }
            is Action.DeletePost -> s.copy(
                posts = s.posts.filterNot { it.id == action.id },
                engine = s.engine.copy(
                    events = s.engine.events.filterNot { it.postId == action.id },
                    carry = s.engine.carry.filterKeys { !it.startsWith("${action.id}:") }),
                session = s.session.copy(openPostId = null)
            )

            is Action.AddComment -> if (action.text.isBlank()) s else posts {
                if (it.id == action.postId) it.copy(
                    commentCount = (it.commentCount + 1).stat(),
                    comments = (it.comments + ownComment(
                        action.text,
                        action.parentId?.takeIf { parent -> it.comments.any { comment -> comment.id == parent } })).threadWindow()
                ) else it
            }

            is Action.LikeComment -> posts { p ->
                if (p.id == action.postId) p.copy(comments = p.comments.map {
                    if (it.id == action.commentId) it.copy(
                        liked = !it.liked,
                        likes = (it.likes + if (it.liked) -1 else 1).stat()
                    ) else it
                }) else p
            }

            is Action.StoryReply -> s.copy(stories = s.stories.map {
                if (it.id == action.id && action.text.isNotBlank()) it.copy(
                    replies = (it.replies + action.text.trim().take(500)).takeLast(100)
                ) else it
            })

            is Action.Highlight -> s.copy(stories = s.stories.map {
                if (it.id == action.id) it.copy(
                    highlight = action.name.trim().take(30)
                ) else it
            })

            is Action.DeleteStory -> s.copy(
                stories = s.stories.filterNot { it.id == action.id },
                session = s.session.copy(openStoryId = null)
            )

            is Action.Follow -> {
                val person = s.people.find { it.id == action.personId }
                    ?: s.activity.find { it.person.id == action.personId }?.person ?: s.posts.flatMap { it.comments }
                        .find { it.person.id == action.personId }?.person
                    ?: s.activeLive?.chat?.find { it.person.id == action.personId }?.person
                if (person == null) s else {
                    val following = !person.followed
                    s.copy(people = s.people.filterNot { it.id == person.id } + person.copy(followed = following),
                        profile = s.profile.copy(following = (s.profile.following + if (following) 1 else -1).stat()))
                }
            }

            is Action.PostStats -> posts {
                if (it.id == action.id) it.copy(
                    likes = action.likes.stat(),
                    commentCount = action.comments.stat(),
                    views = action.views.stat(),
                    frozen = action.frozen,
                    viralPotential = action.potential.coerceIn(0, 100),
                    comments = it.comments.threadWindow(action.comments.coerceIn(0, 180).toInt())
                ) else it
            }

            is Action.AccountStats -> s.copy(
                profile = s.profile.copy(
                    followers = action.followers.stat(),
                    following = action.following.stat(),
                    visits = action.visits.stat()
                )
            )

            is Action.AddFollowers -> s.copy(
                profile = s.profile.copy(
                    followers = (s.profile.followers + action.count.coerceIn(
                        -LIMIT,
                        LIMIT
                    )).stat()
                )
            )

            is Action.AddViews -> posts {
                if (it.id == action.id) it.copy(
                    views = (it.views + action.count.coerceIn(
                        -LIMIT,
                        LIMIT
                    )).stat()
                ) else it
            }

            is Action.Viral -> s.copy(engine = s.engine.copy(events = s.engine.events.filterNot { it.postId == action.id } + ViralEvent(
                action.id,
                s.engine.elapsedSeconds,
                600.0,
                action.strength
            )),
                events = (listOf(
                    GrowthEvent(
                        id,
                        EventKind.VIRAL,
                        "Your post is taking off",
                        "A ten-minute simulated wave is building. You can end it at any time.",
                        now,
                        action.id
                    )
                ) + s.events).take(200))

            is Action.EndViral -> s.copy(
                engine = s.engine.copy(events = if (action.id == null) emptyList() else s.engine.events.filterNot { it.postId == action.id }),
                posts = s.posts.map { if (action.id == null || it.id == action.id) it.copy(viralUntil = 0.0) else it })

            is Action.StartLive -> {
                val config = action.config.validated()
                if (s.activeLive != null) s.copy(session = s.session.copy(screen = "live")) else s.copy(
                    activeLive = LiveSession(
                        id,
                        config,
                        now,
                        viewers = config.startingViewers,
                        peakViewers = config.startingViewers,
                        seed = now
                    ), session = UiSession(screen = "live")
                )
            }

            Action.StopLive -> s.copy(
                activeLive = null,
                liveHistory = s.activeLive?.let { (listOf(it.copy(endedAt = now)) + s.liveHistory).take(30) }
                    ?: s.liveHistory,
                session = UiSession(screen = "history"))

            Action.LiveLike -> s.copy(activeLive = s.activeLive?.let { it.copy(likes = (it.likes + 1).stat()) })
            Action.LiveHype -> s.copy(activeLive = s.activeLive?.let {
                it.copy(hypeUntil = it.elapsedSeconds + 150.0)
            })
            Action.LiveDehype -> s.copy(activeLive = s.activeLive?.let {
                it.copy(hypeUntil = 0.0)
            })

            is Action.LiveComment -> if (action.text.isBlank()) s else s.copy(activeLive = s.activeLive?.let {
                it.copy(
                    chat = (it.chat + ownComment(action.text)).takeLast(80),
                    totalComments = (it.totalComments + 1).stat()
                )
            })

            Action.ReadEvents -> s.copy(
                events = s.events.map { it.copy(read = true) },
                activity = s.activity.map { it.copy(read = true) },
                acknowledgedFollowers = s.profile.followers,
                acknowledgedLikes = s.posts.sumOf { it.likes }
            )

            Action.Refresh -> s

            Action.ResetStatistics -> s.copy(
                profile = s.profile.copy(followers = 0, visits = 0),
                posts = s.posts.map {
                    it.copy(
                        likes = 0,
                        views = 0,
                        commentCount = 0,
                        comments = emptyList(),
                        liked = false,
                        viralUntil = 0.0
                    )
                },
                stories = s.stories.map { it.copy(views = 0) },
                engine = s.engine.copy(
                    carry = emptyMap(),
                    events = emptyList(),
                    eventCooldown = 0.0,
                    ambientMultiplier = 1.0,
                    ambientUntil = 0.0,
                    storySurgeUntil = 0.0,
                    commentFloodUntil = 0.0
                ),
                events = emptyList()
            )
        }
    }
}
