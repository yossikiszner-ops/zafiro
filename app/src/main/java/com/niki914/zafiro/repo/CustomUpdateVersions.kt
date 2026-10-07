package com.niki914.zafiro.repo

internal object CustomUpdateVersions {
    fun newer(remote: String, installed: String): Boolean {
        fun parts(value: String): List<Int>? = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-zafiro\\.(\\d+))?$").matchEntire(value)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: 0 }
        val next = parts(remote) ?: return false
        val current = parts(installed) ?: return false
        next.indices.forEach { index ->
            if (next[index] != current[index]) return next[index] > current[index]
        }
        return false
    }
}
