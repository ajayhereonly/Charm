package com.example.python

data class PyParam(
    val name: String,
    val defaultExpr: PyExpr? = null,
    val isVarArgs: Boolean = false,
    val isKwArgs: Boolean = false
)

data class PyArg(
    val name: String? = null,
    val expr: PyExpr,
    val isStar: Boolean = false,
    val isDoubleStar: Boolean = false
)

sealed interface PyStmt {
    val line: Int
}

data class StmtExpr(val expr: PyExpr, override val line: Int) : PyStmt
data class StmtAssign(val targets: List<PyExpr>, val value: PyExpr, override val line: Int) : PyStmt
data class StmtAugAssign(val target: PyExpr, val op: TokType, val value: PyExpr, override val line: Int) : PyStmt
data class StmtIf(
    val branches: List<Pair<PyExpr, List<PyStmt>>>,
    val elseBody: List<PyStmt>,
    override val line: Int
) : PyStmt
data class StmtWhile(val cond: PyExpr, val body: List<PyStmt>, override val line: Int) : PyStmt
data class StmtFor(val target: PyExpr, val iterable: PyExpr, val body: List<PyStmt>, override val line: Int) : PyStmt
data class StmtDef(
    val name: String,
    val params: List<PyParam>,
    val body: List<PyStmt>,
    val decorators: List<PyExpr> = emptyList(),
    override val line: Int
) : PyStmt
data class StmtClass(
    val name: String,
    val bases: List<PyExpr>,
    val body: List<PyStmt>,
    override val line: Int
) : PyStmt
data class StmtReturn(val expr: PyExpr?, override val line: Int) : PyStmt
data class StmtBreak(override val line: Int) : PyStmt
data class StmtContinue(override val line: Int) : PyStmt
data class StmtPass(override val line: Int) : PyStmt
data class StmtImport(val names: List<Pair<String, String?>>, override val line: Int) : PyStmt
data class StmtFromImport(val module: String, val names: List<Pair<String, String?>>, override val line: Int) : PyStmt
data class StmtTry(
    val tryBody: List<PyStmt>,
    val handlers: List<Triple<String?, String?, List<PyStmt>>>, // (excType, alias, body)
    val finallyBody: List<PyStmt>,
    override val line: Int
) : PyStmt
data class StmtRaise(val expr: PyExpr?, override val line: Int) : PyStmt
data class StmtWith(val expr: PyExpr, val alias: String?, val body: List<PyStmt>, override val line: Int) : PyStmt
data class StmtGlobal(val names: List<String>, override val line: Int) : PyStmt
data class StmtAssert(val cond: PyExpr, val msg: PyExpr?, override val line: Int) : PyStmt
data class StmtDel(val target: PyExpr, override val line: Int) : PyStmt

sealed interface PyExpr {
    val line: Int
}

data class ExprLiteral(val value: PyVal, override val line: Int) : PyExpr
data class ExprFString(val template: String, override val line: Int) : PyExpr
data class ExprIdent(val name: String, override val line: Int) : PyExpr
data class ExprUnary(val op: TokType, val operand: PyExpr, override val line: Int) : PyExpr
data class ExprBinary(val left: PyExpr, val op: TokType, val right: PyExpr, override val line: Int) : PyExpr
data class ExprCompare(val left: PyExpr, val ops: List<Pair<String, PyExpr>>, override val line: Int) : PyExpr
data class ExprBoolOp(val op: TokType, val values: List<PyExpr>, override val line: Int) : PyExpr
data class ExprTernary(val ifTrue: PyExpr, val cond: PyExpr, val ifFalse: PyExpr, override val line: Int) : PyExpr
data class ExprLambda(val params: List<PyParam>, val body: PyExpr, override val line: Int) : PyExpr
data class ExprCall(val callee: PyExpr, val args: List<PyArg>, override val line: Int) : PyExpr
data class ExprAttr(val obj: PyExpr, val attr: String, override val line: Int) : PyExpr
data class ExprSubscript(val obj: PyExpr, val index: PyExpr, override val line: Int) : PyExpr
data class ExprSlice(val obj: PyExpr, val start: PyExpr?, val stop: PyExpr?, val step: PyExpr?, override val line: Int) : PyExpr
data class ExprList(val items: List<PyExpr>, override val line: Int) : PyExpr
data class ExprTuple(val items: List<PyExpr>, override val line: Int) : PyExpr
data class ExprDict(val entries: List<Pair<PyExpr, PyExpr>>, override val line: Int) : PyExpr
data class ExprSet(val items: List<PyExpr>, override val line: Int) : PyExpr
data class ExprListComp(val expr: PyExpr, val target: PyExpr, val iterable: PyExpr, val cond: PyExpr?, override val line: Int) : PyExpr
data class ExprDictComp(val keyExpr: PyExpr, val valExpr: PyExpr, val target: PyExpr, val iterable: PyExpr, val cond: PyExpr?, override val line: Int) : PyExpr
