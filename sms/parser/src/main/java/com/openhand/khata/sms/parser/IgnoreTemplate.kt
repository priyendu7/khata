package com.openhand.khata.sms.parser

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException

/**
 * "Ignore messages like this" (PRD feature 7): a pattern for one SMS that also matches the same
 * message with other numbers, or other words where the user tapped them. The rest of the SMS is
 * fixed text, so a different message from the same sender doesn't match.
 *
 * Built with [PatternParts.literal] like a parser rule, checked with [RegexSubset], and run on
 * RE2J, so a template can't freeze the app either.
 */
object IgnoreTemplate {
    /** Any text in place of a run of tapped words. */
    private const val CHANGING = ".+?"

    /**
     * The pattern for [body], with the [changing] words (from [RuleMaker.words]) as parts that
     * change. Next to each other, they become one part, so a name can have more or fewer words.
     */
    fun pattern(body: String, changing: Collection<RuleMaker.Word>): String {
        val text = body.trim()
        val offset = body.indexOf(text)
        val spans = changing.sortedBy { it.start }.fold(mutableListOf<IntRange>()) { runs, word ->
            val start = word.start - offset
            val end = word.end - offset
            val last = runs.lastOrNull()
            if (last != null && text.substring(last.last, start).isBlank()) {
                runs[runs.lastIndex] = last.first until end
            } else {
                runs += start until end
            }
            runs
        }
        val out = StringBuilder("^\\s*")
        var at = 0
        spans.forEach { span ->
            out.append(PatternParts.literal(text.substring(at, span.first))).append(CHANGING)
            at = span.last + 1
        }
        return out.append(PatternParts.literal(text.substring(at))).append("\\s*$").toString()
    }

    /** [pattern] compiled, or null if it isn't a template this version can run. */
    fun compile(pattern: String): Compiled? =
        if (RegexSubset.namedGroups(pattern)?.isEmpty() == true) {
            try {
                Compiled(Pattern.compile(pattern, SmsParser.PATTERN_FLAGS or Pattern.DOTALL))
            } catch (_: PatternSyntaxException) {
                null
            }
        } else {
            null
        }

    /** A template ready to run; the whole SMS must match. */
    class Compiled internal constructor(private val pattern: Pattern) {
        fun matches(body: String): Boolean = pattern.matcher(body).matches()
    }
}

/** An ignore rule the user made (PRD feature 7). [pattern] is null to ignore the whole sender. */
data class IgnoreRule(val id: Long, val header: String, val pattern: String?)

/** The enabled [IgnoreRule]s, ready to check. Templates that don't compile are skipped. */
class IgnoreRules(rules: List<IgnoreRule> = emptyList()) {
    private val senders: Map<String, Long> =
        rules.filter { it.pattern == null }.associate { it.header to it.id }
    private val templates = rules.mapNotNull { rule ->
        rule.pattern?.let(IgnoreTemplate::compile)?.let { Triple(rule.id, rule.header, it) }
    }

    /** The rule ignoring [header], checked before any text is read. */
    fun senderRule(header: String): Long? = senders[header]

    /** The first template from [header] that [body] matches. */
    fun likeThisRule(header: String, body: String): Long? = templates.firstOrNull { (_, from, p) ->
        from == header && p.matches(body)
    }?.first
}
