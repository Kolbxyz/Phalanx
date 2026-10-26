package org.phalanx.android.core.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryPointsTest {
    private val events = listOf("\$a", "\$b", "\$c")

    @Test
    fun `a point keeps the first message it cites`() {
        val points = pointsOf("- Ana ships 2.1 on Friday [2][3]\n- Bo asks about CI [1]", events)
        assertEquals(
            listOf(SummaryPoint("Ana ships 2.1 on Friday", "\$b"), SummaryPoint("Bo asks about CI", "\$a")),
            points
        )
    }

    @Test
    fun `a citation out of range leads nowhere`() =
        assertEquals(listOf(SummaryPoint("Nothing", null)), pointsOf("* Nothing [9]", events))

    @Test
    fun `a point without citation leads nowhere`() =
        assertEquals(listOf(SummaryPoint("Plain point", null)), pointsOf("1. Plain point\n\n", events))

    @Test
    fun `citations are hidden while the reply is written`() =
        assertEquals("- Deploy is blocked\n- Bo", withoutCitations("- Deploy is blocked [1, 2]\n- Bo ["))
}
