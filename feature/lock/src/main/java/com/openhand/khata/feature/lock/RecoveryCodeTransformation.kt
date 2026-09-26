package com.openhand.khata.feature.lock

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Shows a recovery code as `ABCD-EFGH-2345-6789` while the field holds only the 16 characters. */
internal object RecoveryCodeTransformation : VisualTransformation {
    private const val GROUP = 4

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = raw.chunked(GROUP).joinToString("-")

        // A dash sits after every full group that is followed by more characters.
        fun dashesBefore(offset: Int) = (1..(raw.length - 1) / GROUP).count { it * GROUP < offset }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + dashesBefore(offset)

            override fun transformedToOriginal(offset: Int): Int {
                val dashes = (1..(raw.length - 1) / GROUP).count { it * (GROUP + 1) - 1 < offset }
                return (offset - dashes).coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
