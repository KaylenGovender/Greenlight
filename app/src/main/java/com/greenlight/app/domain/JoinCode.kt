package com.greenlight.app.domain

/** A couple or group code found in a link or a pasted message. */
sealed interface JoinCode {
    /** Formatted like the server's codes: "ABCD-1234" / "K7M-2QX". */
    val code: String

    data class Couple(override val code: String) : JoinCode
    data class Group(override val code: String) : JoinCode

    companion object {
        const val LINK_BASE = "https://greenlight-za.web.app/join/"

        /** Must match GROUP_ALPHABET in functions/src/logic.ts (no 0/O, 1/I/L). */
        private const val GROUP_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val LINK = Regex("""/join/([A-Za-z0-9-]{6,9})""")
        private val COUPLE = Regex("""(?<![A-Z0-9])([A-Z]{4})[-\s]?([0-9]{4})(?![A-Z0-9])""")
        private val GROUP_DASHED = Regex("""(?<![A-Z0-9])([A-Z0-9]{3})-([A-Z0-9]{3})(?![A-Z0-9])""")
        private val GROUP_PLAIN = Regex("""(?<![A-Z0-9])([A-Z0-9]{6})(?![A-Z0-9])""")

        /** The share link for a code: https://greenlight-za.web.app/join/K7M2QX */
        fun link(code: String) = LINK_BASE + code.filter { it.isLetterOrDigit() }.uppercase()

        /** Reads a single code ("abcd1234", "K7M 2QX", …). */
        fun parse(raw: String?): JoinCode? {
            val clean = raw.orEmpty().uppercase().filter { it.isLetterOrDigit() }
            return when {
                clean.length == 8 && clean.take(4).all { it in 'A'..'Z' } && clean.drop(4).all { it.isDigit() } ->
                    Couple(clean.take(4) + "-" + clean.drop(4))
                clean.length == 6 && clean.all { it in GROUP_ALPHABET } -> Group(clean.take(3) + "-" + clean.drop(3))
                else -> null
            }
        }

        /**
         * Finds a code anywhere in pasted text, e.g. a whole WhatsApp message:
         * "Join "Braai Crew" on Greenlight: https://…/join/K7M2QX (or enter code K7M-2QX)".
         * Links win; then couple codes; then dashed group codes; then a bare 6-character code that
         * has both letters and digits (so ordinary words like "PLEASE" aren't mistaken for one).
         */
        fun extract(text: String?): JoinCode? {
            if (text.isNullOrBlank()) return null
            LINK.find(text)?.let { m -> parse(m.groupValues[1])?.let { return it } }
            val up = text.uppercase()
            COUPLE.find(up)?.let { return Couple("${it.groupValues[1]}-${it.groupValues[2]}") }
            GROUP_DASHED.findAll(up).forEach { m -> parse(m.value)?.let { return it } }
            GROUP_PLAIN.findAll(up).forEach { m ->
                val v = m.value
                if (v.any { it.isDigit() } && v.any { it.isLetter() }) parse(v)?.let { return it }
            }
            return null
        }
    }
}
