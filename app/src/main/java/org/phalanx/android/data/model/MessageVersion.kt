package org.phalanx.android.data.model

import androidx.compose.runtime.Immutable

/** One version of an edited message: the original first, then each edit as its sender made it. */
@Immutable
data class MessageVersion(val body: MessageBody, val timestampMillis: Long)
