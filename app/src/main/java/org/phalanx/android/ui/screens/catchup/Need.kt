package org.phalanx.android.ui.screens.catchup

import androidx.compose.runtime.Immutable
import org.phalanx.android.core.ai.CatchUp

/** What [kind] says in this catch-up, or null when there is none. */
internal fun CatchUp.textOf(kind: NeedKind): String? = when (kind) {
    NeedKind.Waiting -> waiting
    NeedKind.Promised -> promised
}

internal fun CatchUp.without(kind: NeedKind): CatchUp = with(kind, null)

internal fun CatchUp.with(kind: NeedKind, text: String?): CatchUp = when (kind) {
    NeedKind.Waiting -> copy(waiting = text)
    NeedKind.Promised -> copy(promised = text)
}

/** One thing that needs the user, from one room's catch-up. */
internal data class Need(val card: CatchUpCard, val kind: NeedKind, val text: String)

/** Everything that needs the user across [cards], in the cards' order: waiting first in each room. */
internal fun needsOf(cards: List<CatchUpCard>): List<Need> = cards.flatMap { card ->
    val catchUp = (card.state as? CardState.Done)?.catchUp ?: return@flatMap emptyList()
    NeedKind.entries.mapNotNull { kind -> catchUp.textOf(kind)?.let { Need(card, kind, it) } }
}

/** Where a need's reminder stands: not asked for, a time being picked, or set and still undoable. */
@Immutable
internal sealed interface Reminding {
    data object Idle : Reminding

    data object Picking : Reminding

    data class Set(val reminderId: String?) : Reminding
}
