package io.github.akrishna87.lumen

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A memory on For You: a title, a line under it, and the photos its slideshow plays. */
data class Memory(val key: String, val title: String, val subtitle: String, val photos: List<MediaItem>) {
    /** The card's photo: the most recent one. */
    val cover: MediaItem get() = photos.last()
}

object Memories {
    /** A month needs this many photos to become a memory. */
    private const val MIN_PHOTOS = 6
    private const val RECENT_MONTHS = 6

    /**
     * "On This Day" (this date in earlier years), then each of the last few months with enough
     * photos, newest first. Videos are left out: the slideshow plays photos. [items] is the
     * Library's list, so hidden photos never appear.
     */
    fun build(items: List<MediaItem>, today: LocalDate = LocalDate.now()): List<Memory> {
        val photos = items.filter { !it.isVideo }
        val memories = ArrayList<Memory>()

        val onThisDay = photos.filter { it.date.monthValue == today.monthValue && it.date.dayOfMonth == today.dayOfMonth && it.date.year < today.year }
        if (onThisDay.isNotEmpty()) {
            val years = onThisDay.map { today.year - it.date.year }.distinct().sorted()
            val ago = if (years.size == 1) Format.plural(years[0], "Year") + " Ago" else years.joinToString(", ") + " Years Ago"
            memories += Memory("day", "On This Day", today.format(DateTimeFormatter.ofPattern("MMMM d")) + " • " + ago, onThisDay)
        }

        Grouping.months(photos).asReversed()
            .filter { it.items.size >= MIN_PHOTOS }
            .take(RECENT_MONTHS)
            .forEach { month -> memories += Memory("month:${month.year}-${month.month}", month.title, Format.plural(month.items.size, "Photo"), month.items) }
        return memories
    }
}
