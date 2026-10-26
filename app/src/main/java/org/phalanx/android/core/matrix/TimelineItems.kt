package org.phalanx.android.core.matrix

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.phalanx.android.data.model.TimelineEntry

/**
 * Mirrors an SDK timeline as [TimelineEntry] rows, oldest first. Diffs arrive on the SDK's thread
 * and address positions in its list, so items that are not shown are kept as rows without an entry.
 */
internal class TimelineItems(private val context: MappingContext) {
    private val lock = Any()
    private val items = ArrayList<TimelineRow>()
    private val mutableEntries = MutableStateFlow<List<TimelineEntry>>(emptyList())
    val entries: StateFlow<List<TimelineEntry>> = mutableEntries

    fun apply(diffs: List<TimelineDiff>) {
        synchronized(lock) {
            diffs.forEach(::applyOne)
            mutableEntries.value = entriesOf(items)
        }
    }

    private fun applyOne(diff: TimelineDiff) {
        when (diff) {
            is TimelineDiff.Append -> diff.values.forEach { items.add(it.toRow(context)) }
            is TimelineDiff.PushBack -> items.add(diff.value.toRow(context))
            is TimelineDiff.PushFront -> items.add(0, diff.value.toRow(context))
            is TimelineDiff.Insert -> items.add(diff.index.toInt(), diff.value.toRow(context))
            is TimelineDiff.Set -> items[diff.index.toInt()] = diff.value.toRow(context)
            is TimelineDiff.Remove -> items.removeAt(diff.index.toInt())
            is TimelineDiff.Reset -> {
                items.clear()
                diff.values.forEach { items.add(it.toRow(context)) }
            }
            is TimelineDiff.Truncate -> items.subList(diff.length.toInt(), items.size).clear()
            TimelineDiff.PopBack -> items.removeLastOrNull()
            TimelineDiff.PopFront -> items.removeFirstOrNull()
            TimelineDiff.Clear -> items.clear()
        }
    }
}

/**
 * The rows' entries, in order. A read receipt on an item that is not shown (a member joining, a
 * state change) counts on the message before it: whoever read that far read the message too.
 */
internal fun entriesOf(rows: List<TimelineRow>): List<TimelineEntry> {
    val entries = ArrayList<TimelineEntry>(rows.size)
    var lastMessage = -1
    for (row in rows) {
        when (val entry = row.entry) {
            is TimelineEntry.Message -> {
                entries.add(entry.withReaders(row.readBy))
                lastMessage = entries.lastIndex
            }
            null -> if (lastMessage >= 0) {
                entries[lastMessage] = (entries[lastMessage] as TimelineEntry.Message).withReaders(row.readBy)
            }
            else -> entries.add(entry)
        }
    }
    return entries
}

private fun TimelineEntry.Message.withReaders(readers: Set<String>) =
    if (readers.isEmpty()) this else copy(readBy = readBy + readers)
