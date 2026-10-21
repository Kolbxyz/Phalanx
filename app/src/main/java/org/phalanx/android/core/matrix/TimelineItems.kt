package org.phalanx.android.core.matrix

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.phalanx.android.data.model.TimelineEntry

/**
 * Mirrors an SDK timeline as [TimelineEntry] rows, oldest first. Diffs arrive on the SDK's thread
 * and address positions in its list, so items that are not shown are kept as nulls.
 */
internal class TimelineItems(private val context: MappingContext) {
    private val lock = Any()
    private val items = ArrayList<TimelineEntry?>()
    private val mutableEntries = MutableStateFlow<List<TimelineEntry>>(emptyList())
    val entries: StateFlow<List<TimelineEntry>> = mutableEntries

    fun apply(diffs: List<TimelineDiff>) {
        synchronized(lock) {
            diffs.forEach(::applyOne)
            mutableEntries.value = items.filterNotNull()
        }
    }

    private fun applyOne(diff: TimelineDiff) {
        when (diff) {
            is TimelineDiff.Append -> diff.values.forEach { items.add(it.toEntry(context)) }
            is TimelineDiff.PushBack -> items.add(diff.value.toEntry(context))
            is TimelineDiff.PushFront -> items.add(0, diff.value.toEntry(context))
            is TimelineDiff.Insert -> items.add(diff.index.toInt(), diff.value.toEntry(context))
            is TimelineDiff.Set -> items[diff.index.toInt()] = diff.value.toEntry(context)
            is TimelineDiff.Remove -> items.removeAt(diff.index.toInt())
            is TimelineDiff.Reset -> {
                items.clear()
                diff.values.forEach { items.add(it.toEntry(context)) }
            }
            is TimelineDiff.Truncate -> items.subList(diff.length.toInt(), items.size).clear()
            TimelineDiff.PopBack -> items.removeLastOrNull()
            TimelineDiff.PopFront -> items.removeFirstOrNull()
            TimelineDiff.Clear -> items.clear()
        }
    }
}
