package nl.fondsradar.core

import kotlinx.serialization.json.Json

class FeedFout(message: String, cause: Throwable? = null) : Exception(message, cause)

object FeedParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(tekst: String): Feed = try {
        json.decodeFromString(Feed.serializer(), tekst)
    } catch (e: Exception) {
        throw FeedFout("Kon regelingen.json niet lezen: ${e.message}", e)
    }
}
