package org.phalanx.android.core.ai.actions

/**
 * Whether [text] may hold something to act on: a date or time, a day, a duration, an address, a
 * phone number or a parcel tracking code, in French or English. Cheap, so it runs on every message;
 * only a match offers to look closer with the model, which never runs unasked.
 */
internal fun mayHoldActions(text: String): Boolean = HINTS.any { it.containsMatchIn(text) }

private val HINTS = listOf(
    // 20h, 20h30, 8:15, 8 pm, 9am
    Regex("""\b\d{1,2}\s*(?:h\s*\d{0,2}|:\d{2}|\s?[ap]\.?m\.?)\b""", RegexOption.IGNORE_CASE),
    // 30/10, 30.10.2026, 2026-10-30
    Regex("""\b\d{1,2}[/.]\d{1,2}(?:[/.]\d{2,4})?\b|\b\d{4}-\d{2}-\d{2}\b"""),
    Regex(
        """\b(?:today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|weekend|""" +
            """next week|aujourd'hui|aujourd’hui|ce soir|demain|lundi|mardi|mercredi|jeudi|vendredi|samedi|""" +
            """dimanche|semaine prochaine)\b""",
        RegexOption.IGNORE_CASE
    ),
    // in 20 minutes, dans 2 h, for 10 min
    Regex("""\b\d{1,3}\s*(?:min(?:ute)?s?|hours?|heures?|h)\b""", RegexOption.IGNORE_CASE),
    Regex(
        """\b(?:remind|reminder|rappelle|rappel|alarm|alarme|réveil|wake me|meeting|réunion|rdv|""" +
            """rendez-vous|appointment|deadline|todo|à faire|don't forget|n'oublie|n’oublie)""",
        RegexOption.IGNORE_CASE
    ),
    // 12 rue de la Paix, 221B Baker Street
    Regex(
        """\b\d{1,4}[a-z]?,?\s+(?:rue|avenue|av\.|boulevard|bd|place|chemin|allée|impasse|quai|street|st\.|""" +
            """road|rd\.|lane|ave)\b""",
        RegexOption.IGNORE_CASE
    ),
    // +33 6 12 34 56 78, 06 12 34 56 78, (555) 123-4567
    Regex("""(?:\+\d{1,3}[\s.-]?)?(?:\(?\d{2,4}\)?[\s.-]?){3,5}\d{2,4}"""),
    // 1Z999AA10123456784, LA123456789FR
    Regex("""\b(?:1Z[0-9A-Z]{16}|[A-Z]{2}\d{9}[A-Z]{2})\b""")
)
