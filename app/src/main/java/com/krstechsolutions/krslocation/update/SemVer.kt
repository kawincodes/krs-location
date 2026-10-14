package com.krstechsolutions.krslocation.update

/** Tiny semantic-version comparison for release tags like "v1.2.0" or "1.2". */
object SemVer {

    fun parse(raw: String): List<Int> =
        raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    /** Negative when [a] is older than [b], zero when equal, positive when newer. */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}
