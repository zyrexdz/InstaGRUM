package com.instagrum.local.simulation

import com.instagrum.local.model.*

/** Lightweight, offline context used to make reactions fit a post or live. */
data class CommentContext(
    val caption: String = "",
    val location: String = "",
    val mediaKind: MediaKind = MediaKind.IMAGE,
    val spokenText: String = "",
)

object CommentGenerator {
    private val emojis = listOf("🔥", "😭", "🤍", "🫶", "✨", "😍", "💀", "👏", "🌿", "💯", "🥹")

    private val short = listOf(
        "bro 😭", "nah this is crazy", "clean", "insane", "wait WHAT", "first", "no way", "💀",
        "felt that", "SO GOOD", "speechless.", "🤍", "🔥🔥", "✨", "🫶", "😍😍", "👏👏👏", "❤️",
        "💯", "🥹", "this goes hard", "pls", "yoooo", "masterpiece", "real", "say less", "wow",
        "okayyy 🔥", "the details", "I see the vision", "not me replaying this", "actually unreal"
    )

    private val leads = listOf(
        "Honestly", "Okay but", "Not gonna lie", "I swear", "Somehow", "Genuinely", "Low key",
        "For real", "Just saying", "Every time", "The way", "No joke", "Can't explain it but",
        "Idk why but", "Once again", "Wait", "Please", "Listen", "Not enough people are talking about",
        "I came back because", "My first thought was", "Nobody warned me that", "The fact that",
        "Respectfully", "I need to know", "This is why", "You really said", "Okay wait"
    )

    private val bodies = listOf(
        "this is exactly my kind of thing", "you make it look easy", "the atmosphere is perfect",
        "I want to be there right now", "this feels so cinematic", "this deserves a spot on my wall",
        "I could look at this all day", "the colors are unreal", "you captured something special",
        "this has so much personality", "I'm saving this for later", "this reminds me to slow down",
        "the little details make it", "I want to try something like this", "this one hits different",
        "you can feel the moment", "this looks better every time", "the light does all the talking",
        "this made scrolling worth it", "the composition is so satisfying", "there is something about this",
        "I was not prepared for how good this is", "this feels like a memory already",
        "you never miss with these", "I keep noticing new things", "the mood is perfect",
        "this is going straight into my favorites", "I understand the obsession now", "the timing is unreal",
        "this belongs in a movie", "I need the story behind this", "the framing is intentional",
        "this feels very much like a Sunday", "the edit is so clean", "you found the exact moment",
        "this is quietly one of my favorites", "I would frame this immediately", "the texture is everything",
        "this has been stuck in my head", "I want to know what happened next", "it feels effortless",
        "the vibe is immaculate", "this is a whole little world", "I felt this before I understood it",
        "the colors are doing so much", "this is the kind of post I follow for"
    )

    private val endings = listOf(
        "today.", "for real.", "and I mean that.", "honestly.", "no notes.", "every single time.",
        "right now.", "in the best way.", "and it shows.", "I can't stop looking.", "please keep posting.",
        "not even exaggerating.", "that is all.", "you get it.", "I needed this.", "respectfully.",
        "I had to say it.", "someone had to say it.", "and now I need the context.", "on repeat."
    )

    private val topicLines = mapOf(
        "food" to listOf(
            "this made me hungry",
            "recipe??",
            "I need a bite of this",
            "okay chef",
            "saving this for dinner",
            "what is that sauce?",
            "the presentation though"
        ),
        "travel" to listOf(
            "adding this to my list",
            "how long were you there?",
            "need the itinerary",
            "one day I'll go here",
            "this is travel motivation",
            "what was your favorite part?"
        ),
        "beach" to listOf(
            "take me with you",
            "the water looks unreal",
            "I can hear the waves",
            "sun therapy",
            "I need this kind of day",
            "beach days never miss"
        ),
        "mountains" to listOf(
            "the view is ridiculous",
            "how was the hike?",
            "worth the climb",
            "I need mountain air",
            "this looks so peaceful",
            "the scale of this is wild"
        ),
        "city" to listOf(
            "city lights hit different",
            "what city is this?",
            "the streets look alive",
            "I love this side of the city",
            "the architecture is so good"
        ),
        "night" to listOf(
            "night photography is undefeated",
            "the lights are perfect",
            "this feels like a scene",
            "midnight energy",
            "the blue hour though"
        ),
        "sunset" to listOf(
            "the sky understood the assignment",
            "sunsets are never the same",
            "golden hour did its thing",
            "I would stop for this too",
            "those colors are unreal"
        ),
        "pet" to listOf(
            "give them a treat from me 🐶",
            "the real star of the post",
            "look at that face",
            "I would never leave",
            "please tell them I said hi",
            "instant follow for the pet"
        ),
        "car" to listOf(
            "what are you driving?",
            "the lines on this are perfect",
            "that interior though",
            "this belongs on a poster",
            "okay I need the full walkaround"
        ),
        "fitness" to listOf(
            "what's your split?",
            "the consistency is inspiring",
            "okay I need to train now",
            "how long did this take?",
            "strong work",
            "drop the routine"
        ),
        "music" to listOf(
            "what song is this?",
            "this needs to be a music video",
            "the sound choice is perfect",
            "adding this to my playlist",
            "I felt that beat"
        ),
        "art" to listOf(
            "the texture is beautiful",
            "how long did this take?",
            "the details are insane",
            "I love the colors",
            "this belongs in a gallery",
            "what inspired this?"
        ),
        "fashion" to listOf(
            "where is this fit from?",
            "the styling is so good",
            "this look is everything",
            "I need the details",
            "you understood the assignment",
            "the color combination!"
        ),
        "coffee" to listOf(
            "coffee run immediately",
            "what's the order?",
            "the perfect little ritual",
            "I can almost smell this",
            "morning done right"
        ),
        "selfie" to listOf(
            "the lighting is perfect",
            "okay face card",
            "this is your color",
            "the confidence is everything",
            "how do you look this good casually?"
        ),
        "nature" to listOf(
            "nature always wins",
            "this feels so calm",
            "I need a quiet day here",
            "the colors are healing",
            "what a beautiful world"
        ),
        "party" to listOf(
            "that looks like a night",
            "the energy is unmatched",
            "wish I was there",
            "okay this is a movie",
            "who was playing?"
        ),
        "work" to listOf(
            "the behind the scenes we needed",
            "how did you pull this off?",
            "the process is so interesting",
            "this is seriously impressive",
            "hard work showing"
        ),
        "gaming" to listOf(
            "what game is this?",
            "the setup is clean",
            "one more round",
            "I need the settings",
            "that play was insane"
        ),
        "weather" to listOf(
            "the weather made this shot",
            "rainy days are underrated",
            "I love this atmosphere",
            "the clouds are perfect",
            "such a cozy mood"
        ),
        "family" to listOf(
            "this is so wholesome",
            "protect this memory",
            "the smiles say everything",
            "love this for you",
            "these are the moments"
        ),
    )

    private val topicKeywords = mapOf(
        "food" to listOf(
            "food",
            "dinner",
            "lunch",
            "breakfast",
            "eat",
            "recipe",
            "restaurant",
            "pizza",
            "pasta",
            "cake",
            "cook"
        ),
        "travel" to listOf("travel", "trip", "vacation", "holiday", "visit", "journey", "airport", "hotel"),
        "beach" to listOf("beach", "ocean", "sea", "coast", "shore", "island", "wave"),
        "mountains" to listOf("mountain", "hike", "hiking", "trail", "peak", "summit", "climb"),
        "city" to listOf("city", "street", "downtown", "urban", "building", "subway", "town"),
        "night" to listOf("night", "midnight", "neon", "dark", "evening"),
        "sunset" to listOf("sunset", "sunrise", "golden hour", "goldenhour", "dusk", "sky"),
        "pet" to listOf("dog", "puppy", "cat", "kitten", "pet", "pup", "animal"),
        "car" to listOf("car", "drive", "truck", "wheel", "engine", "road", "auto"),
        "fitness" to listOf("gym", "fitness", "workout", "training", "lift", "run", "running", "routine"),
        "music" to listOf("song", "music", "album", "concert", "playlist", "listen", "band", "beat"),
        "art" to listOf("art", "paint", "draw", "gallery", "canvas", "sculpture"),
        "fashion" to listOf("outfit", "fashion", "dress", "fit", "style", "wear", "shoes", "jacket"),
        "coffee" to listOf("coffee", "cafe", "café", "espresso", "latte", "brew"),
        "selfie" to listOf("selfie", "portrait", "face", "mirror", "look"),
        "nature" to listOf("nature", "forest", "tree", "flower", "garden", "lake", "river", "green"),
        "party" to listOf("party", "dance", "club", "birthday", "celebrate", "festival"),
        "work" to listOf("work", "office", "project", "process", "studio", "desk", "study", "class"),
        "gaming" to listOf("game", "gaming", "play", "stream", "controller", "setup"),
        "weather" to listOf("rain", "rainy", "cloud", "snow", "storm", "sunny", "weather"),
        "family" to listOf("family", "friend", "friends", "together", "mom", "dad", "sister", "brother"),
    )

    private val liveTexts = listOf(
        "hey from London 👋", "just joined!", "audio is good", "hi everyone", "what did I miss?",
        "love these lives", "where are you right now?", "can you say hi to Maya", "been waiting for this",
        "the chat is moving so fast 😭", "we're here!!", "keep going", "that story is wild", "tell us more",
        "same here", "🤣🤣", "much love from Brazil", "it's 2am here lol", "first live I've caught",
        "this is so chill", "can we get a room tour", "W chat", "🫶🫶🫶", "I agree",
        "that's a good question", "one more story please", "how was your day?", "water break!",
        "thanks for hanging out", "just got here from your post", "wait I missed the beginning", "the vibes are good",
        "please pin that", "I needed this live today", "who else is watching from bed?"
    )

    private val videoLines = listOf(
        "the edit!!",
        "what song is this?",
        "rewatched 5 times",
        "the transition is so clean",
        "how did you film this?",
        "that timing though",
        "this deserves a replay"
    )
    private val genericPool: List<String> = buildList {
        addAll(short)
        addAll(leads.flatMap { lead -> bodies.map { "$lead, $it." } })
        addAll(bodies.flatMap { body -> endings.map { "$body, $it" } })
        addAll(leads.flatMap { lead -> endings.map { "$lead, $it" } })
        addAll(leads.flatMap { lead ->
            bodies.take(32).map { "$lead $it ${emojis[(lead.length + it.length) % emojis.size]}" }
        })
    }.distinct()

    /** Large public pool retained for tests and callers that need generic text. */
    val pool: List<String> = genericPool

    private val realAvatars = (0..23).map { "file:///android_asset/avatars/$it.jpg" }

    private fun identityHash(value: Int): Int {
        var x = value * -1640531527
        x = x xor (x ushr 16)
        x *= 0x45d9f3b
        x = x xor (x ushr 16)
        return x and Int.MAX_VALUE
    }

    /** Odd multiplier coprime with the identity space keeps index -> username collision free. */
    private val identitySpace = NameData.first.size * NameData.handle.size * NameData.patterns * NameData.tail.size

    fun person(index: Int): FakePerson {
        val n = index.coerceAtLeast(0)
        // A bijective scramble inside the space guarantees distinct usernames for
        // distinct indices; only wrapping past the whole space can repeat, and
        // that suffix keeps those apart too.
        val block = n / identitySpace
        val scrambled = ((n % identitySpace).toLong() * 2_654_435_761L % identitySpace).toInt()
        val first = NameData.first[scrambled % NameData.first.size]
        val handle = NameData.handle[(scrambled / NameData.first.size) % NameData.handle.size]
        val pattern = (scrambled / (NameData.first.size * NameData.handle.size)) % NameData.patterns
        val tail =
            NameData.tail[scrambled / (NameData.first.size * NameData.handle.size * NameData.patterns) % NameData.tail.size]
        val h = identityHash(n)
        val clean = first.lowercase()
        val base = when (pattern) {
            0 -> "$clean.$handle$tail"
            1 -> "${clean}_$handle$tail"
            2 -> clean + handle.replace(".", "").replaceFirstChar { it.uppercase() } + tail
            3 -> "$handle.$clean$tail"
            4 -> "$clean$tail.$handle"
            5 -> "its$clean.$handle$tail"
            6 -> "real$clean.$handle$tail"
            else -> "${clean}_${handle}_$tail"
        }
        val username = if (block > 0) "$base$block" else base
        val last =
            listOf("Ortiz", "Novak", "Sharma", "Tanaka", "Silva", "Rossi", "Nguyen", "Mensah", "Costa", "Vega")[h % 10]
        val emoji = emojis[h % emojis.size]
        val display = when ((h / 31).mod(7)) {
            0 -> first
            1 -> "$first ${last.first()}."
            2 -> first.lowercase()
            3 -> "$first $emoji"
            4 -> "$emoji $first"
            5 -> "$first $last"
            else -> "$first ${last.first()}."
        }
        return FakePerson(
            "person-$n",
            username,
            display,
            avatar = realAvatars[n % realAvatars.size],
            colorIndex = n % 24,
            verified = n % 29 == 0
        )
    }

    fun generate(
        seed: Long,
        now: Long,
        id: String,
        live: Boolean = false,
        parentId: String? = null,
        recentTexts: List<String> = emptyList(),
    ): Comment = generate(seed, now, id, CommentContext(), live, parentId, recentTexts)

    fun generate(
        seed: Long,
        now: Long,
        id: String,
        context: CommentContext,
        live: Boolean = false,
        parentId: String? = null,
        recentTexts: List<String> = emptyList(),
    ): Comment {
        val random = SimRandom(seed)
        val source = contextText(context, random, live)
        val recent = recentTexts.takeLast(100).toSet()
        val available = source.filterNot { it in recent }.ifEmpty {
            (if (live) liveTexts else genericPool).filterNot { it in recent }
        }.ifEmpty { listOf("still here for this", "okay wow", "love this") }
        val text = available[random.int(available.size)]
        return Comment(
            id = id,
            person = person((random.next() * 2_000_000).toInt()),
            text = text,
            createdAt = now,
            likes = if (live) 0 else random.int(35).toLong(),
            parentId = parentId,
        )
    }

    private fun contextText(context: CommentContext, random: SimRandom, live: Boolean): List<String> {
        if (live && context.spokenText.isBlank() && context.caption.isBlank() && context.location.isBlank()) return liveTexts
        val haystack = "${context.caption} ${context.location} ${context.spokenText}".lowercase()
        val topics = topicKeywords.filterValues { words -> words.any { word -> haystack.contains(word) } }.keys
        val relevant = topics.flatMap { topicLines[it].orEmpty() }.toMutableList()
        if (context.location.isNotBlank() && random.next() < .35) {
            relevant += listOf(
                "okay but ${context.location} looks incredible",
                "adding ${context.location} to my list",
                "this makes me miss ${context.location}"
            )
        }
        if (context.mediaKind == MediaKind.REEL || context.mediaKind == MediaKind.VIDEO) relevant += videoLines
        val generic = if (live) liveTexts else genericPool
        if (relevant.isEmpty()) return generic
        // Keep most reactions contextual, while retaining generic crowd behavior.
        return buildList {
            addAll(relevant)
            addAll(relevant)
            addAll(generic)
        }
    }
}
