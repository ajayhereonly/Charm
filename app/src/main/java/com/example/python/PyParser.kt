package com.example.python

class PyParser(private val tokens: List<Token>) {
    private var pos = 0

    private fun peek(offset: Int = 0): Token =
        tokens.getOrElse(pos + offset) { tokens.last() }

    private fun advance(): Token = tokens[pos++]

    private fun check(type: TokType): Boolean = peek().type == type

    private fun match(vararg types: TokType): Boolean {
        if (types.any { peek().type == it }) {
            pos++
            return true
        }
        return false
    }

    private fun expect(type: TokType, msg: String = "Expected $type"): Token {
        if (check(type)) return advance()
        throw PyRuntimeException("SyntaxError", "$msg, got '${peek().text}' (${peek().type})", peek().line)
    }

    private fun skipNewlines() {
        while (check(TokType.NEWLINE)) pos++
    }

    fun parseModule(): List<PyStmt> {
        val stmts = mutableListOf<PyStmt>()
        skipNewlines()
        while (!check(TokType.EOF)) {
            stmts.addAll(parseStatement())
            skipNewlines()
        }
        return stmts
    }

    private fun parseStatement(): List<PyStmt> {
        return when (peek().type) {
            TokType.AT -> listOf(parseDecorated())
            TokType.DEF -> listOf(parseDef(emptyList()))
            TokType.CLASS -> listOf(parseClass())
            TokType.IF -> listOf(parseIf())
            TokType.WHILE -> listOf(parseWhile())
            TokType.FOR -> listOf(parseFor())
            TokType.TRY -> listOf(parseTry())
            TokType.WITH -> listOf(parseWith())
            else -> parseSimpleLine()
        }
    }

    private fun parseDecorated(): PyStmt {
        val decorators = mutableListOf<PyExpr>()
        while (match(TokType.AT)) {
            decorators.add(parseExpression())
            expect(TokType.NEWLINE, "Expected newline after decorator")
            skipNewlines()
        }
        return if (check(TokType.DEF)) parseDef(decorators) else parseClass()
    }

    private fun parseDef(decorators: List<PyExpr>): StmtDef {
        val tok = expect(TokType.DEF)
        val name = expect(TokType.IDENT, "Expected function name").text
        expect(TokType.LPAREN)
        val params = parseParams(TokType.RPAREN)
        expect(TokType.RPAREN)
        if (match(TokType.ARROW)) {
            parseExpression() // ignore optional return type annotation
        }
        expect(TokType.COLON)
        val body = parseSuite()
        return StmtDef(name, params, body, decorators, tok.line)
    }

    private fun parseParams(endTok: TokType): List<PyParam> {
        val params = mutableListOf<PyParam>()
        while (!check(endTok) && !check(TokType.EOF)) {
            var isStar = false
            var isDoubleStar = false
            if (match(TokType.DOUBLESTAR)) isDoubleStar = true
            else if (match(TokType.STAR)) isStar = true

            if (check(TokType.COMMA)) { advance(); continue }
            val pName = expect(TokType.IDENT, "Expected parameter name").text
            if (match(TokType.COLON)) {
                parseExpression() // ignore optional type annotation
            }
            val defaultExpr = if (match(TokType.ASSIGN)) parseExpression() else null
            params.add(PyParam(pName, defaultExpr, isStar, isDoubleStar))
            if (!match(TokType.COMMA)) break
        }
        return params
    }

    private fun parseClass(): StmtClass {
        val tok = expect(TokType.CLASS)
        val name = expect(TokType.IDENT, "Expected class name").text
        val bases = mutableListOf<PyExpr>()
        if (match(TokType.LPAREN)) {
            while (!check(TokType.RPAREN) && !check(TokType.EOF)) {
                bases.add(parseExpression())
                if (!match(TokType.COMMA)) break
            }
            expect(TokType.RPAREN)
        }
        expect(TokType.COLON)
        val body = parseSuite()
        return StmtClass(name, bases, body, tok.line)
    }

    private fun parseIf(): StmtIf {
        val tok = expect(TokType.IF)
        val branches = mutableListOf<Pair<PyExpr, List<PyStmt>>>()
        val cond = parseExpression()
        expect(TokType.COLON)
        branches.add(cond to parseSuite())
        skipNewlines()
        while (match(TokType.ELIF)) {
            val eCond = parseExpression()
            expect(TokType.COLON)
            branches.add(eCond to parseSuite())
            skipNewlines()
        }
        val elseBody = if (match(TokType.ELSE)) {
            expect(TokType.COLON)
            parseSuite()
        } else emptyList()
        return StmtIf(branches, elseBody, tok.line)
    }

    private fun parseWhile(): StmtWhile {
        val tok = expect(TokType.WHILE)
        val cond = parseExpression()
        expect(TokType.COLON)
        val body = parseSuite()
        return StmtWhile(cond, body, tok.line)
    }

    private fun parseFor(): StmtFor {
        val tok = expect(TokType.FOR)
        val target = parseTargetList()
        expect(TokType.IN, "Expected 'in' in for loop")
        val iter = parseExprOrTuple()
        expect(TokType.COLON)
        val body = parseSuite()
        return StmtFor(target, iter, body, tok.line)
    }

    private fun parseTry(): StmtTry {
        val tok = expect(TokType.TRY)
        expect(TokType.COLON)
        val tryBody = parseSuite()
        skipNewlines()
        val handlers = mutableListOf<Triple<String?, String?, List<PyStmt>>>()
        while (match(TokType.EXCEPT)) {
            var excType: String? = null
            var alias: String? = null
            if (!check(TokType.COLON)) {
                excType = expect(TokType.IDENT, "Expected exception type").text
                while (match(TokType.DOT)) {
                    excType = "$excType.${expect(TokType.IDENT).text}"
                }
                if (match(TokType.AS)) {
                    alias = expect(TokType.IDENT, "Expected exception alias").text
                }
            }
            expect(TokType.COLON)
            handlers.add(Triple(excType, alias, parseSuite()))
            skipNewlines()
        }
        val finallyBody = if (match(TokType.FINALLY)) {
            expect(TokType.COLON)
            parseSuite()
        } else emptyList()
        return StmtTry(tryBody, handlers, finallyBody, tok.line)
    }

    private fun parseWith(): StmtWith {
        val tok = expect(TokType.WITH)
        val expr = parseExpression()
        val alias = if (match(TokType.AS)) expect(TokType.IDENT).text else null
        expect(TokType.COLON)
        val body = parseSuite()
        return StmtWith(expr, alias, body, tok.line)
    }

    private fun parseSuite(): List<PyStmt> {
        if (!check(TokType.NEWLINE)) {
            return parseSimpleLine()
        }
        expect(TokType.NEWLINE)
        skipNewlines()
        expect(TokType.INDENT, "Expected indented block")
        val stmts = mutableListOf<PyStmt>()
        skipNewlines()
        while (!check(TokType.DEDENT) && !check(TokType.EOF)) {
            stmts.addAll(parseStatement())
            skipNewlines()
        }
        match(TokType.DEDENT)
        return stmts
    }

    private fun parseSimpleLine(): List<PyStmt> {
        val stmts = mutableListOf<PyStmt>()
        stmts.add(parseSmallStmt())
        while (match(TokType.SEMICOLON)) {
            if (check(TokType.NEWLINE) || check(TokType.EOF)) break
            stmts.add(parseSmallStmt())
        }
        if (!check(TokType.EOF) && !check(TokType.DEDENT)) {
            expect(TokType.NEWLINE, "Expected end of line")
        }
        return stmts
    }

    private fun parseSmallStmt(): PyStmt {
        val tok = peek()
        return when (tok.type) {
            TokType.PASS -> { advance(); StmtPass(tok.line) }
            TokType.BREAK -> { advance(); StmtBreak(tok.line) }
            TokType.CONTINUE -> { advance(); StmtContinue(tok.line) }
            TokType.RETURN -> {
                advance()
                val expr = if (check(TokType.NEWLINE) || check(TokType.SEMICOLON) || check(TokType.EOF)) null
                else parseExprOrTuple()
                StmtReturn(expr, tok.line)
            }
            TokType.RAISE -> {
                advance()
                val expr = if (check(TokType.NEWLINE) || check(TokType.SEMICOLON) || check(TokType.EOF)) null
                else parseExpression()
                StmtRaise(expr, tok.line)
            }
            TokType.GLOBAL, TokType.NONLOCAL -> {
                advance()
                val names = mutableListOf(expect(TokType.IDENT).text)
                while (match(TokType.COMMA)) names.add(expect(TokType.IDENT).text)
                StmtGlobal(names, tok.line)
            }
            TokType.ASSERT -> {
                advance()
                val cond = parseExpression()
                val msg = if (match(TokType.COMMA)) parseExpression() else null
                StmtAssert(cond, msg, tok.line)
            }
            TokType.DEL -> {
                advance()
                StmtDel(parseExpression(), tok.line)
            }
            TokType.IMPORT -> {
                advance()
                val items = mutableListOf<Pair<String, String?>>()
                do {
                    val mod = parseDottedName()
                    val alias = if (match(TokType.AS)) expect(TokType.IDENT).text else null
                    items.add(mod to alias)
                } while (match(TokType.COMMA))
                StmtImport(items, tok.line)
            }
            TokType.FROM -> {
                advance()
                val mod = parseDottedName()
                expect(TokType.IMPORT, "Expected 'import'")
                val items = mutableListOf<Pair<String, String?>>()
                val parens = match(TokType.LPAREN)
                if (match(TokType.STAR)) {
                    items.add("*" to null)
                } else {
                    do {
                        if (parens && check(TokType.RPAREN)) break
                        val name = expect(TokType.IDENT).text
                        val alias = if (match(TokType.AS)) expect(TokType.IDENT).text else null
                        items.add(name to alias)
                    } while (match(TokType.COMMA))
                }
                if (parens) expect(TokType.RPAREN)
                StmtFromImport(mod, items, tok.line)
            }
            else -> {
                val first = parseExprOrTuple()
                // Ignore type annotation in `x: int = 5`
                if (match(TokType.COLON)) {
                    parseExpression()
                    if (match(TokType.ASSIGN)) {
                        return StmtAssign(listOf(first), parseExprOrTuple(), tok.line)
                    }
                    return StmtPass(tok.line)
                }
                val augOps = setOf(
                    TokType.PLUSEQ, TokType.MINUSEQ, TokType.STAREQ, TokType.SLASHEQ,
                    TokType.DOUBLESLASHEQ, TokType.PERCENTEQ, TokType.DOUBLESTAREQ
                )
                if (peek().type in augOps) {
                    val op = advance().type
                    return StmtAugAssign(first, op, parseExprOrTuple(), tok.line)
                }
                if (check(TokType.ASSIGN)) {
                    val targets = mutableListOf(first)
                    while (match(TokType.ASSIGN)) {
                        targets.add(parseExprOrTuple())
                    }
                    val value = targets.removeLast()
                    return StmtAssign(targets, value, tok.line)
                }
                StmtExpr(first, tok.line)
            }
        }
    }

    private fun parseDottedName(): String {
        val sb = StringBuilder(expect(TokType.IDENT, "Expected module name").text)
        while (match(TokType.DOT)) {
            sb.append('.').append(expect(TokType.IDENT).text)
        }
        return sb.toString()
    }

    private fun parseTargetList(): PyExpr {
        val first = parsePrimary()
        if (!check(TokType.COMMA)) return first
        val items = mutableListOf(first)
        while (match(TokType.COMMA)) {
            if (check(TokType.IN) || check(TokType.ASSIGN)) break
            items.add(parsePrimary())
        }
        return ExprTuple(items, first.line)
    }

    private fun parseExprOrTuple(): PyExpr {
        val first = parseExpression()
        if (!check(TokType.COMMA)) return first
        val items = mutableListOf(first)
        while (match(TokType.COMMA)) {
            if (check(TokType.NEWLINE) || check(TokType.EOF) || check(TokType.COLON) ||
                check(TokType.RPAREN) || check(TokType.RBRACKET) || check(TokType.ASSIGN) || check(TokType.SEMICOLON)
            ) break
            items.add(parseExpression())
        }
        return ExprTuple(items, first.line)
    }

    fun parseExpression(): PyExpr {
        if (check(TokType.LAMBDA)) return parseLambda()
        val expr = parseOr()
        if (match(TokType.IF)) {
            val cond = parseOr()
            expect(TokType.ELSE, "Expected 'else' in conditional expression")
            val ifFalse = parseExpression()
            return ExprTernary(expr, cond, ifFalse, expr.line)
        }
        return expr
    }

    private fun parseLambda(): ExprLambda {
        val tok = expect(TokType.LAMBDA)
        val params = if (check(TokType.COLON)) emptyList() else parseParams(TokType.COLON)
        expect(TokType.COLON)
        val body = parseExpression()
        return ExprLambda(params, body, tok.line)
    }

    private fun parseOr(): PyExpr {
        val first = parseAnd()
        if (!check(TokType.OR)) return first
        val vals = mutableListOf(first)
        while (match(TokType.OR)) vals.add(parseAnd())
        return ExprBoolOp(TokType.OR, vals, first.line)
    }

    private fun parseAnd(): PyExpr {
        val first = parseNot()
        if (!check(TokType.AND)) return first
        val vals = mutableListOf(first)
        while (match(TokType.AND)) vals.add(parseNot())
        return ExprBoolOp(TokType.AND, vals, first.line)
    }

    private fun parseNot(): PyExpr {
        if (check(TokType.NOT)) {
            val tok = advance()
            return ExprUnary(TokType.NOT, parseNot(), tok.line)
        }
        return parseComparison()
    }

    private fun parseComparison(): PyExpr {
        val left = parseBitOr()
        val ops = mutableListOf<Pair<String, PyExpr>>()
        while (true) {
            val opStr = when {
                match(TokType.EQ) -> "=="
                match(TokType.NEQ) -> "!="
                match(TokType.LT) -> "<"
                match(TokType.GT) -> ">"
                match(TokType.LTE) -> "<="
                match(TokType.GTE) -> ">="
                match(TokType.IN) -> "in"
                check(TokType.NOT) && peek(1).type == TokType.IN -> { pos += 2; "not in" }
                check(TokType.IS) && peek(1).type == TokType.NOT -> { pos += 2; "is not" }
                match(TokType.IS) -> "is"
                else -> null
            } ?: break
            ops.add(opStr to parseBitOr())
        }
        return if (ops.isEmpty()) left else ExprCompare(left, ops, left.line)
    }

    private fun parseBitOr(): PyExpr {
        var left = parseBitXor()
        while (match(TokType.BITOR)) left = ExprBinary(left, TokType.BITOR, parseBitXor(), left.line)
        return left
    }

    private fun parseBitXor(): PyExpr {
        var left = parseBitAnd()
        while (match(TokType.BITXOR)) left = ExprBinary(left, TokType.BITXOR, parseBitAnd(), left.line)
        return left
    }

    private fun parseBitAnd(): PyExpr {
        var left = parseShift()
        while (match(TokType.BITAND)) left = ExprBinary(left, TokType.BITAND, parseShift(), left.line)
        return left
    }

    private fun parseShift(): PyExpr {
        var left = parseAdd()
        while (check(TokType.LSHIFT) || check(TokType.RSHIFT)) {
            val op = advance().type
            left = ExprBinary(left, op, parseAdd(), left.line)
        }
        return left
    }

    private fun parseAdd(): PyExpr {
        var left = parseMul()
        while (check(TokType.PLUS) || check(TokType.MINUS)) {
            val op = advance().type
            left = ExprBinary(left, op, parseMul(), left.line)
        }
        return left
    }

    private fun parseMul(): PyExpr {
        var left = parseUnary()
        while (check(TokType.STAR) || check(TokType.SLASH) || check(TokType.DOUBLESLASH) || check(TokType.PERCENT) || check(TokType.AT)) {
            val op = advance().type
            left = ExprBinary(left, op, parseUnary(), left.line)
        }
        return left
    }

    private fun parseUnary(): PyExpr {
        if (check(TokType.PLUS) || check(TokType.MINUS) || check(TokType.BITNOT)) {
            val tok = advance()
            return ExprUnary(tok.type, parseUnary(), tok.line)
        }
        return parsePower()
    }

    private fun parsePower(): PyExpr {
        val base = parsePrimary()
        if (match(TokType.DOUBLESTAR)) {
            return ExprBinary(base, TokType.DOUBLESTAR, parseUnary(), base.line)
        }
        return base
    }

    private fun parsePrimary(): PyExpr {
        var expr = parseAtom()
        while (true) {
            expr = when {
                match(TokType.DOT) -> {
                    val attr = expect(TokType.IDENT, "Expected attribute name").text
                    ExprAttr(expr, attr, expr.line)
                }
                match(TokType.LPAREN) -> {
                    val args = mutableListOf<PyArg>()
                    while (!check(TokType.RPAREN) && !check(TokType.EOF)) {
                        when {
                            match(TokType.DOUBLESTAR) -> args.add(PyArg(null, parseExpression(), isDoubleStar = true))
                            match(TokType.STAR) -> args.add(PyArg(null, parseExpression(), isStar = true))
                            check(TokType.IDENT) && peek(1).type == TokType.ASSIGN -> {
                                val kw = advance().text
                                advance() // '='
                                args.add(PyArg(kw, parseExpression()))
                            }
                            else -> {
                                val e = parseExpression()
                                if (check(TokType.FOR)) {
                                    // Generator expression inside function call -> treat as list comp
                                    args.add(PyArg(null, finishListComp(e)))
                                } else {
                                    args.add(PyArg(null, e))
                                }
                            }
                        }
                        if (!match(TokType.COMMA)) break
                    }
                    expect(TokType.RPAREN)
                    ExprCall(expr, args, expr.line)
                }
                match(TokType.LBRACKET) -> {
                    var start: PyExpr? = null
                    var stop: PyExpr? = null
                    var step: PyExpr? = null
                    var isSlice = false
                    if (!check(TokType.COLON)) {
                        start = parseExprOrTuple()
                    }
                    if (match(TokType.COLON)) {
                        isSlice = true
                        if (!check(TokType.COLON) && !check(TokType.RBRACKET)) {
                            stop = parseExpression()
                        }
                        if (match(TokType.COLON)) {
                            if (!check(TokType.RBRACKET)) {
                                step = parseExpression()
                            }
                        }
                    }
                    expect(TokType.RBRACKET)
                    if (isSlice) ExprSlice(expr, start, stop, step, expr.line)
                    else ExprSubscript(expr, start!!, expr.line)
                }
                else -> return expr
            }
        }
    }

    private fun finishListComp(firstExpr: PyExpr): ExprListComp {
        expect(TokType.FOR)
        val target = parseTargetList()
        expect(TokType.IN)
        val iter = parseOr()
        val cond = if (match(TokType.IF)) parseOr() else null
        return ExprListComp(firstExpr, target, iter, cond, firstExpr.line)
    }

    private fun parseAtom(): PyExpr {
        val tok = peek()
        return when (tok.type) {
            TokType.INT -> {
                advance()
                val v = when {
                    tok.text.startsWith("0x", true) -> tok.text.substring(2).toLong(16)
                    tok.text.startsWith("0b", true) -> tok.text.substring(2).toLong(2)
                    tok.text.startsWith("0o", true) -> tok.text.substring(2).toLong(8)
                    else -> tok.text.toLong()
                }
                ExprLiteral(PyInt(v), tok.line)
            }
            TokType.FLOAT -> { advance(); ExprLiteral(PyFloat(tok.text.toDouble()), tok.line) }
            TokType.STRING -> {
                advance()
                val sb = StringBuilder(tok.text)
                while (check(TokType.STRING)) sb.append(advance().text)
                ExprLiteral(PyStr(sb.toString()), tok.line)
            }
            TokType.FSTRING -> { advance(); ExprFString(tok.text, tok.line) }
            TokType.TRUE -> { advance(); ExprLiteral(PyBool(true), tok.line) }
            TokType.FALSE -> { advance(); ExprLiteral(PyBool(false), tok.line) }
            TokType.NONE -> { advance(); ExprLiteral(PyNone, tok.line) }
            TokType.IDENT -> { advance(); ExprIdent(tok.text, tok.line) }
            TokType.LPAREN -> {
                advance()
                if (match(TokType.RPAREN)) return ExprTuple(emptyList(), tok.line)
                val first = parseExpression()
                if (check(TokType.FOR)) {
                    val comp = finishListComp(first)
                    expect(TokType.RPAREN)
                    return comp
                }
                if (match(TokType.COMMA)) {
                    val items = mutableListOf(first)
                    while (!check(TokType.RPAREN) && !check(TokType.EOF)) {
                        items.add(parseExpression())
                        if (!match(TokType.COMMA)) break
                    }
                    expect(TokType.RPAREN)
                    ExprTuple(items, tok.line)
                } else {
                    expect(TokType.RPAREN)
                    first
                }
            }
            TokType.LBRACKET -> {
                advance()
                if (match(TokType.RBRACKET)) return ExprList(emptyList(), tok.line)
                val first = parseExpression()
                if (check(TokType.FOR)) {
                    val comp = finishListComp(first)
                    expect(TokType.RBRACKET)
                    return comp
                }
                val items = mutableListOf(first)
                while (match(TokType.COMMA)) {
                    if (check(TokType.RBRACKET)) break
                    items.add(parseExpression())
                }
                expect(TokType.RBRACKET)
                ExprList(items, tok.line)
            }
            TokType.LBRACE -> {
                advance()
                if (match(TokType.RBRACE)) return ExprDict(emptyList(), tok.line)
                val first = parseExpression()
                if (match(TokType.COLON)) {
                    val firstVal = parseExpression()
                    if (match(TokType.FOR)) {
                        val target = parseTargetList()
                        expect(TokType.IN)
                        val iter = parseOr()
                        val cond = if (match(TokType.IF)) parseOr() else null
                        expect(TokType.RBRACE)
                        return ExprDictComp(first, firstVal, target, iter, cond, tok.line)
                    }
                    val entries = mutableListOf(first to firstVal)
                    while (match(TokType.COMMA)) {
                        if (check(TokType.RBRACE)) break
                        val k = parseExpression()
                        expect(TokType.COLON)
                        val v = parseExpression()
                        entries.add(k to v)
                    }
                    expect(TokType.RBRACE)
                    ExprDict(entries, tok.line)
                } else {
                    val items = mutableListOf(first)
                    while (match(TokType.COMMA)) {
                        if (check(TokType.RBRACE)) break
                        items.add(parseExpression())
                    }
                    expect(TokType.RBRACE)
                    ExprSet(items, tok.line)
                }
            }
            else -> throw PyRuntimeException("SyntaxError", "Unexpected token '${tok.text}' (${tok.type})", tok.line)
        }
    }
}
