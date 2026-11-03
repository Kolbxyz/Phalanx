package org.phalanx.android.core.format

import android.app.PendingIntent

/** What kind of thing [FoundEntity] is, for its icon. */
enum class EntityKind { Date, Phone, Email, Address, Flight }

/**
 * Something the phone's text classifier recognised in a message: a date, a phone number, an
 * address. [actions] are the classifier's own, already titled in the phone's language.
 */
data class FoundEntity(val text: String, val kind: EntityKind, val actions: List<EntityAction>)

/** One thing to do with a [FoundEntity], such as "Call" or "Add to calendar". */
data class EntityAction(val title: String, val intent: PendingIntent)
