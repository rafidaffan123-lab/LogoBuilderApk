package com.example.logobuilder.data

import com.example.logobuilder.state.RecentProject

/** Tab/newline-delimited encoding of the recent list (metadata only, no file contents). */
object RecentCodec {
    const val MAX = 10

    fun encode(list: List<RecentProject>): String =
        list.joinToString("\n") { listOf(esc(it.uri), esc(it.name), it.size, it.sha256, it.openedAt).joinToString("\t") }

    fun decode(s: String): List<RecentProject> = s.lines().mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val p = line.split("\t")
        if (p.size != 5) return@mapNotNull null
        val size = p[2].toLongOrNull() ?: return@mapNotNull null
        val at = p[4].toLongOrNull() ?: return@mapNotNull null
        RecentProject(p[0], p[1], size, p[3], at)
    }

    fun add(list: List<RecentProject>, item: RecentProject): List<RecentProject> =
        (listOf(item) + list.filter { it.uri != item.uri }).take(MAX)

    private fun esc(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
