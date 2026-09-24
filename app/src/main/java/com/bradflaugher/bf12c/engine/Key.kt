package com.bradflaugher.bf12c.engine

/**
 * Every physical key on the 12c, in keyboard order. [code] is the HP row/column
 * keycode shown in program mode (digits use the digit itself).
 * [label], [f] and [g] are the primary, gold (above) and blue (below) legends.
 */
enum class Key(val code: Int, val label: String, val f: String? = null, val g: String? = null) {
    N(11, "n", "AMORT", "12×"),
    I(12, "i", "INT", "12÷"),
    PV(13, "PV", "NPV", "CF₀"),
    PMT(14, "PMT", "RND", "CFⱼ"),
    FV(15, "FV", "IRR", "Nⱼ"),
    CHS(16, "CHS", null, "DATE"),
    D7(7, "7", null, "BEG"),
    D8(8, "8", null, "END"),
    D9(9, "9", null, "MEM"),
    DIV(10, "÷"),

    YX(21, "yˣ", "PRICE", "√x"),
    RECIP(22, "1/x", "YTM", "eˣ"),
    PCT_T(23, "%T", "SL", "LN"),
    DELTA_PCT(24, "Δ%", "SOYD", "FRAC"),
    PCT(25, "%", "DB", "INTG"),
    EEX(26, "EEX", "ALL", "ΔDYS"),
    D4(4, "4", null, "D.MY"),
    D5(5, "5", null, "M.DY"),
    D6(6, "6", null, "x̄w"),
    MUL(20, "×", null, "x²"),

    RS(31, "R/S", "P/R", "PSE"),
    SST(32, "SST", "Σ", "BST"),
    RDN(33, "R↓", "PRGM", "GTO"),
    SWAP(34, "x≷y", "FIN", "x≤y"),
    CLX(35, "CLx", "REG", "x=0"),
    ENTER(36, "ENTER", "PREFIX", "LSTx"),
    D1(1, "1", null, "x̂,r"),
    D2(2, "2", null, "ŷ,r"),
    D3(3, "3", null, "n!"),
    SUB(30, "−", null, "←"),

    ON(41, "ON"),
    F(42, "f"),
    G(43, "g"),
    STO(44, "STO"),
    RCL(45, "RCL"),
    D0(0, "0", null, "x̄"),
    DOT(48, ".", "SCI", "s"),
    SIGMA_PLUS(49, "Σ+", null, "Σ−"),
    ADD(40, "+");

    val digit: Int? get() = if (code in 0..9 && this != DIV) code else null

    companion object {
        /** Landscape layout: 4 rows of 10. ENTER occupies row 3 and row 4 of column 6. */
        val ROWS: List<List<Key>> = listOf(
            listOf(N, I, PV, PMT, FV, CHS, D7, D8, D9, DIV),
            listOf(YX, RECIP, PCT_T, DELTA_PCT, PCT, EEX, D4, D5, D6, MUL),
            listOf(RS, SST, RDN, SWAP, CLX, ENTER, D1, D2, D3, SUB),
            listOf(ON, F, G, STO, RCL, ENTER, D0, DOT, SIGMA_PLUS, ADD),
        )

        /** The gold "CLEAR" bracket spans these keys' f legends. */
        val CLEAR_BRACKET = setOf(SST, RDN, SWAP, CLX)

        private val byDigit = entries.filter { it.digit != null }.associateBy { it.digit!! }
        fun digit(d: Int): Key = byDigit.getValue(d)
        fun byCode(code: Int): Key? = entries.firstOrNull { it.code == code }
    }
}
