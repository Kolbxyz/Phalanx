package org.phalanx.android.core.ai.actions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionHintsTest {
    @Test
    fun `dates, times and plans are noticed in french and english`() = listOf(
        "on se voit vendredi à 20h au Café X",
        "dinner tomorrow at 8pm?",
        "rdv le 30/10",
        "lance un minuteur de 10 min",
        "rappelle-moi d'appeler maman",
        "c'est au 12 rue de la Paix",
        "mon numéro : 06 12 34 56 78",
        "tracking: LA123456789FR"
    ).forEach { assertTrue(it, mayHoldActions(it)) }

    @Test
    fun `small talk is left alone`() = listOf("haha trop bien", "ok merci !", "see you", "😂😂")
        .forEach { assertFalse(it, mayHoldActions(it)) }
}
