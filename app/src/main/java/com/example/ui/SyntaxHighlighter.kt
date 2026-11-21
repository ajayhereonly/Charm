package com.example.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.PythonAmber
import com.example.ui.theme.SyntaxBlue
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxGreen
import com.example.ui.theme.SyntaxOrange
import com.example.ui.theme.SyntaxViolet
import com.example.ui.theme.TextPrimary

object PythonSyntaxHighlighter : VisualTransformation {

    private val keywords = setOf(
        "def", "class", "return", "if", "elif", "else", "while", "for", "in",
        "break", "continue", "pass", "import", "from", "as", "try", "except",
        "finally", "raise", "with", "lambda", "and", "or", "not", "is",
        "True", "False", "None", "global", "nonlocal", "assert", "del", "yield"
    )

    private val builtins = setOf(
        "print", "len", "range", "enumerate", "zip", "map", "filter", "sum",
        "min", "max", "abs", "round", "sorted", "reversed", "int", "float",
        "str", "bool", "list", "tuple", "dict", "set", "type", "isinstance",
        "self", "py3d", "np", "pd", "plt", "math", "random"
    )

    private val tokenRegex = Regex(
        """(#[^\n]*)|(\"\"\"[\s\S]*?\"\"\"|'''[\s\S]*?'''|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')|(\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b)|(\b[A-Za-z_][A-Za-z0-9_]*\b)|(@[A-Za-z_][A-Za-z0-9_]*)"""
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val highlighted = buildAnnotatedString {
            append(raw)
            addStyle(SpanStyle(color = TextPrimary), 0, raw.length)

            for (match in tokenRegex.findAll(raw)) {
                val start = match.range.first
                val end = match.range.last + 1
                when {
                    match.groups[1] != null -> addStyle(
                        SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic),
                        start, end
                    )
                    match.groups[2] != null -> addStyle(
                        SpanStyle(color = SyntaxGreen),
                        start, end
                    )
                    match.groups[3] != null -> addStyle(
                        SpanStyle(color = SyntaxOrange),
                        start, end
                    )
                    match.groups[5] != null -> addStyle(
                        SpanStyle(color = PythonAmber, fontWeight = FontWeight.Medium),
                        start, end
                    )
                    match.groups[4] != null -> {
                        val word = match.value
                        when {
                            word in keywords -> addStyle(
                                SpanStyle(color = SyntaxViolet, fontWeight = FontWeight.SemiBold),
                                start, end
                            )
                            word in builtins -> addStyle(
                                SpanStyle(color = ElectricCyan),
                                start, end
                            )
                            end < raw.length && raw[end] == '(' -> addStyle(
                                SpanStyle(color = SyntaxBlue),
                                start, end
                            )
                        }
                    }
                }
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
