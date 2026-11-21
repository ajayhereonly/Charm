package com.example.python

enum class TokType {
    INDENT, DEDENT, NEWLINE, EOF,
    IDENT, INT, FLOAT, STRING, FSTRING,
    // Keywords
    DEF, CLASS, RETURN, IF, ELIF, ELSE, WHILE, FOR, IN, BREAK, CONTINUE, PASS,
    IMPORT, FROM, AS, TRY, EXCEPT, FINALLY, RAISE, WITH, LAMBDA,
    AND, OR, NOT, IS, TRUE, FALSE, NONE, GLOBAL, NONLOCAL, ASSERT, DEL, YIELD,
    // Operators & Delimiters
    PLUS, MINUS, STAR, SLASH, DOUBLESLASH, PERCENT, DOUBLESTAR, AT,
    EQ, NEQ, LT, GT, LTE, GTE,
    ASSIGN, PLUSEQ, MINUSEQ, STAREQ, SLASHEQ, DOUBLESLASHEQ, PERCENTEQ, DOUBLESTAREQ,
    BITAND, BITOR, BITXOR, BITNOT, LSHIFT, RSHIFT,
    LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE,
    COMMA, COLON, DOT, SEMICOLON, ARROW
}

data class Token(val type: TokType, val text: String, val line: Int)

class PyLexer(private val source: String) {
    private val keywords = mapOf(
        "def" to TokType.DEF,
        "class" to TokType.CLASS,
        "return" to TokType.RETURN,
        "if" to TokType.IF,
        "elif" to TokType.ELIF,
        "else" to TokType.ELSE,
        "while" to TokType.WHILE,
        "for" to TokType.FOR,
        "in" to TokType.IN,
        "break" to TokType.BREAK,
        "continue" to TokType.CONTINUE,
        "pass" to TokType.PASS,
        "import" to TokType.IMPORT,
        "from" to TokType.FROM,
        "as" to TokType.AS,
        "try" to TokType.TRY,
        "except" to TokType.EXCEPT,
        "finally" to TokType.FINALLY,
        "raise" to TokType.RAISE,
        "with" to TokType.WITH,
        "lambda" to TokType.LAMBDA,
        "and" to TokType.AND,
        "or" to TokType.OR,
        "not" to TokType.NOT,
        "is" to TokType.IS,
        "True" to TokType.TRUE,
        "False" to TokType.FALSE,
        "None" to TokType.NONE,
        "global" to TokType.GLOBAL,
        "nonlocal" to TokType.NONLOCAL,
        "assert" to TokType.ASSERT,
        "del" to TokType.DEL,
        "yield" to TokType.YIELD
    )

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        val indentStack = ArrayDeque<Int>().apply { addLast(0) }
        val src = source.replace("\r\n", "\n").replace("\r", "\n")
        var i = 0
        var line = 1
        var atLineStart = true
        var bracketDepth = 0

        while (i < src.length) {
            if (atLineStart && bracketDepth == 0) {
                var spaces = 0
                var j = i
                while (j < src.length && (src[j] == ' ' || src[j] == '\t')) {
                    spaces += if (src[j] == '\t') 4 else 1
                    j++
                }
                if (j >= src.length || src[j] == '\n' || src[j] == '#') {
                    // Blank or comment-only line
                    i = j
                    if (i < src.length && src[i] == '#') {
                        while (i < src.length && src[i] != '\n') i++
                    }
                    if (i < src.length && src[i] == '\n') {
                        i++
                        line++
                    }
                    continue
                }
                i = j
                atLineStart = false
                val currentIndent = indentStack.last()
                if (spaces > currentIndent) {
                    indentStack.addLast(spaces)
                    tokens.add(Token(TokType.INDENT, "", line))
                } else if (spaces < currentIndent) {
                    while (indentStack.size > 1 && indentStack.last() > spaces) {
                        indentStack.removeLast()
                        tokens.add(Token(TokType.DEDENT, "", line))
                    }
                    if (indentStack.last() != spaces) {
                        throw PyRuntimeException("IndentationError", "Unindent does not match outer indentation level", line)
                    }
                }
            }

            val ch = src[i]
            when {
                ch == ' ' || ch == '\t' -> i++
                ch == '\\' && i + 1 < src.length && src[i + 1] == '\n' -> {
                    i += 2
                    line++
                }
                ch == '#' -> {
                    while (i < src.length && src[i] != '\n') i++
                }
                ch == '\n' -> {
                    if (bracketDepth == 0) {
                        if (tokens.isNotEmpty() && tokens.last().type != TokType.NEWLINE && tokens.last().type != TokType.INDENT) {
                            tokens.add(Token(TokType.NEWLINE, "\n", line))
                        }
                        atLineStart = true
                    }
                    i++
                    line++
                }
                // String prefixes (f, r, b, u, fr, rf)
                (ch == 'f' || ch == 'F' || ch == 'r' || ch == 'R' || ch == 'b' || ch == 'B' || ch == 'u' || ch == 'U') &&
                    i + 1 < src.length && (src[i + 1] == '"' || src[i + 1] == '\'' ||
                    ((src[i + 1] == 'r' || src[i + 1] == 'R' || src[i + 1] == 'f' || src[i + 1] == 'F') &&
                        i + 2 < src.length && (src[i + 2] == '"' || src[i + 2] == '\''))) -> {
                    var prefix = ch.lowercaseChar().toString()
                    i++
                    if (src[i] != '"' && src[i] != '\'') {
                        prefix += src[i].lowercaseChar()
                        i++
                    }
                    val isF = 'f' in prefix
                    val isR = 'r' in prefix
                    val (strVal, nextIdx, linesAdded) = readString(src, i, line, isR)
                    tokens.add(Token(if (isF) TokType.FSTRING else TokType.STRING, strVal, line))
                    i = nextIdx
                    line += linesAdded
                }
                ch == '"' || ch == '\'' -> {
                    val (strVal, nextIdx, linesAdded) = readString(src, i, line, isRaw = false)
                    tokens.add(Token(TokType.STRING, strVal, line))
                    i = nextIdx
                    line += linesAdded
                }
                ch.isDigit() || (ch == '.' && i + 1 < src.length && src[i + 1].isDigit()) -> {
                    val start = i
                    var isFloat = false
                    if (ch == '0' && i + 1 < src.length && (src[i + 1] == 'x' || src[i + 1] == 'X' || src[i + 1] == 'b' || src[i + 1] == 'B' || src[i + 1] == 'o' || src[i + 1] == 'O')) {
                        i += 2
                        while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                        tokens.add(Token(TokType.INT, src.substring(start, i).replace("_", ""), line))
                    } else {
                        while (i < src.length && (src[i].isDigit() || src[i] == '_')) i++
                        if (i < src.length && src[i] == '.' && !(i + 1 < src.length && src[i + 1] == '.')) {
                            isFloat = true
                            i++
                            while (i < src.length && (src[i].isDigit() || src[i] == '_')) i++
                        }
                        if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
                            isFloat = true
                            i++
                            if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
                            while (i < src.length && (src[i].isDigit() || src[i] == '_')) i++
                        }
                        if (i < src.length && (src[i] == 'j' || src[i] == 'J')) {
                            i++ // complex suffix tolerated as float
                            isFloat = true
                        }
                        val numText = src.substring(start, i).replace("_", "").trimEnd('j', 'J')
                        tokens.add(Token(if (isFloat) TokType.FLOAT else TokType.INT, numText, line))
                    }
                }
                ch.isLetter() || ch == '_' -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    val word = src.substring(start, i)
                    tokens.add(Token(keywords[word] ?: TokType.IDENT, word, line))
                }
                else -> {
                    val two = if (i + 1 < src.length) src.substring(i, i + 2) else ""
                    val three = if (i + 2 < src.length) src.substring(i, i + 3) else ""
                    when {
                        three == "**=" -> { tokens.add(Token(TokType.DOUBLESTAREQ, three, line)); i += 3 }
                        three == "//=" -> { tokens.add(Token(TokType.DOUBLESLASHEQ, three, line)); i += 3 }
                        two == "**" -> { tokens.add(Token(TokType.DOUBLESTAR, two, line)); i += 2 }
                        two == "//" -> { tokens.add(Token(TokType.DOUBLESLASH, two, line)); i += 2 }
                        two == "==" -> { tokens.add(Token(TokType.EQ, two, line)); i += 2 }
                        two == "!=" -> { tokens.add(Token(TokType.NEQ, two, line)); i += 2 }
                        two == "<=" -> { tokens.add(Token(TokType.LTE, two, line)); i += 2 }
                        two == ">=" -> { tokens.add(Token(TokType.GTE, two, line)); i += 2 }
                        two == "+=" -> { tokens.add(Token(TokType.PLUSEQ, two, line)); i += 2 }
                        two == "-=" -> { tokens.add(Token(TokType.MINUSEQ, two, line)); i += 2 }
                        two == "*=" -> { tokens.add(Token(TokType.STAREQ, two, line)); i += 2 }
                        two == "/=" -> { tokens.add(Token(TokType.SLASHEQ, two, line)); i += 2 }
                        two == "%=" -> { tokens.add(Token(TokType.PERCENTEQ, two, line)); i += 2 }
                        two == "<<" -> { tokens.add(Token(TokType.LSHIFT, two, line)); i += 2 }
                        two == ">>" -> { tokens.add(Token(TokType.RSHIFT, two, line)); i += 2 }
                        two == "->" -> { tokens.add(Token(TokType.ARROW, two, line)); i += 2 }
                        else -> {
                            val single = when (ch) {
                                '+' -> TokType.PLUS
                                '-' -> TokType.MINUS
                                '*' -> TokType.STAR
                                '/' -> TokType.SLASH
                                '%' -> TokType.PERCENT
                                '@' -> TokType.AT
                                '<' -> TokType.LT
                                '>' -> TokType.GT
                                '=' -> TokType.ASSIGN
                                '&' -> TokType.BITAND
                                '|' -> TokType.BITOR
                                '^' -> TokType.BITXOR
                                '~' -> TokType.BITNOT
                                '(' -> { bracketDepth++; TokType.LPAREN }
                                ')' -> { bracketDepth = (bracketDepth - 1).coerceAtLeast(0); TokType.RPAREN }
                                '[' -> { bracketDepth++; TokType.LBRACKET }
                                ']' -> { bracketDepth = (bracketDepth - 1).coerceAtLeast(0); TokType.RBRACKET }
                                '{' -> { bracketDepth++; TokType.LBRACE }
                                '}' -> { bracketDepth = (bracketDepth - 1).coerceAtLeast(0); TokType.RBRACE }
                                ',' -> TokType.COMMA
                                ':' -> TokType.COLON
                                '.' -> TokType.DOT
                                ';' -> TokType.SEMICOLON
                                else -> throw PyRuntimeException("SyntaxError", "Unexpected character '$ch'", line)
                            }
                            tokens.add(Token(single, ch.toString(), line))
                            i++
                        }
                    }
                }
            }
        }

        if (tokens.isNotEmpty() && tokens.last().type != TokType.NEWLINE) {
            tokens.add(Token(TokType.NEWLINE, "\n", line))
        }
        while (indentStack.size > 1) {
            indentStack.removeLast()
            tokens.add(Token(TokType.DEDENT, "", line))
        }
        tokens.add(Token(TokType.EOF, "", line))
        return tokens
    }

    private fun readString(src: String, startIdx: Int, startLine: Int, isRaw: Boolean): Triple<String, Int, Int> {
        val quote = src[startIdx]
        val isTriple = startIdx + 2 < src.length && src[startIdx + 1] == quote && src[startIdx + 2] == quote
        var i = startIdx + if (isTriple) 3 else 1
        val sb = StringBuilder()
        var linesAdded = 0

        while (i < src.length) {
            if (isTriple) {
                if (i + 2 < src.length && src[i] == quote && src[i + 1] == quote && src[i + 2] == quote) {
                    return Triple(sb.toString(), i + 3, linesAdded)
                }
            } else {
                if (src[i] == quote) {
                    return Triple(sb.toString(), i + 1, linesAdded)
                }
                if (src[i] == '\n') {
                    throw PyRuntimeException("SyntaxError", "Unterminated string literal", startLine + linesAdded)
                }
            }

            val c = src[i]
            if (c == '\n') linesAdded++
            if (c == '\\' && !isRaw && i + 1 < src.length) {
                val next = src[i + 1]
                when (next) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '\'' -> { sb.append('\''); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\n' -> { linesAdded++; i += 2 }
                    else -> { sb.append('\\'); sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        throw PyRuntimeException("SyntaxError", "Unterminated string literal", startLine)
    }
}
