package com.openhand.khata.sms.parser

/**
 * Checks that a pattern uses only regex features that behave the same in RE2 (the app, see
 * [SmsParser]) and JavaScript (the parser website), so a rule tested on the website works the same
 * on the phone.
 *
 * Not allowed: lookahead and lookbehind `(?=` `(?!` `(?<=` `(?<!` and backreferences `\1` `\k<…>`
 * (RE2 has none), atomic groups `(?>`, `(?P<name>`, inline flags `(?i)` (matching is always
 * case-insensitive instead), possessive quantifiers `a*+`, escapes that differ between the two
 * (`\A \Z \z \G \p \P \h \H \R \X \Q \E \C \c \u \k \x{…}`), and nested or intersected
 * character classes. Named groups are allowed only with the names in [RuleFormat.GROUPS].
 */
internal object RegexSubset {
    /** The named groups [pattern] uses, or null if it uses anything outside the subset. */
    fun namedGroups(pattern: String): Set<String>? = Scanner(pattern).run()

    private class Scanner(private val pattern: String) {
        private val groups = mutableSetOf<String>()
        private var i = 0
        private var inClass = false
        private var lastWasQuantifier = false
        private var ok = true

        fun run(): Set<String>? {
            while (ok && i < pattern.length) step()
            return groups.takeIf { ok && !inClass }
        }

        private fun at(offset: Int): Char? = pattern.getOrNull(i + offset)

        /** Reads one token starting at [i] and moves past it. */
        private fun step() {
            val c = pattern[i]
            var quantifier = false
            when {
                c == '\\' -> escape()
                inClass -> classChar(c)
                c == '[' -> classStart()
                c == '(' && at(1) == '?' -> specialGroup()
                c == '+' && lastWasQuantifier -> ok = false // possessive: a*+
                c in QUANTIFIER_ENDS -> quantifier = true
            }
            // `?` after a quantifier makes it lazy (fine in both) and ends the quantifier.
            lastWasQuantifier = quantifier && !(c == '?' && lastWasQuantifier)
            i++
        }

        private fun escape() {
            val next = at(1)
            ok = next != null && next !in UNSUPPORTED_ESCAPES && !(next == 'x' && at(2) == '{')
            i++
        }

        private fun classChar(c: Char) {
            when {
                c == ']' -> inClass = false
                c == '[' -> ok = false
                c == '&' && at(1) == '&' -> ok = false
            }
        }

        private fun classStart() {
            inClass = true
            if (at(1) == '^') i++
            // A `]` right after `[` or `[^` is a literal in RE2 but ends the class in JavaScript.
            if (at(1) == ']') ok = false
        }

        /** `(?:` is fine; `(?<name>` only with a known, unused name; anything else isn't. */
        private fun specialGroup() {
            when (at(2)) {
                ':' -> i += 2
                '<' -> namedGroup()
                else -> ok = false
            }
        }

        private fun namedGroup() {
            val start = i + NAMED_GROUP_OPEN.length
            val end = pattern.indexOf('>', start)
            val name = if (end < 0) "" else pattern.substring(start, end)
            ok = name in RuleFormat.GROUPS && groups.add(name)
            i = if (end < 0) pattern.length else end
        }
    }

    private const val NAMED_GROUP_OPEN = "(?<"
    private const val UNSUPPORTED_ESCAPES = "AZzGpPhHRXQECcuk123456789"
    private val QUANTIFIER_ENDS = setOf('*', '+', '?', '}')
}
