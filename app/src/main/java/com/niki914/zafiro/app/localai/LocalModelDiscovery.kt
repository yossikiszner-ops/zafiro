package com.niki914.zafiro.app.localai

import com.niki914.zafiro.chat.routing.ModelArtifact
import java.io.File

/** Bounded discovery in readable application storage; other applications' private files are inaccessible. */
internal object LocalModelDiscovery {
    fun discover(roots: List<File>, catalog: List<ModelArtifact> = ModelArtifact.candidates): Map<ModelArtifact, File> {
        val found = linkedMapOf<ModelArtifact, File>()
        var examined = 0
        for (root in roots.distinct()) {
            if (!root.isDirectory) continue
            for (file in root.walkTopDown().maxDepth(3)) {
                if (++examined > 500) return found
                if (!file.isFile || !file.name.endsWith(".litertlm", ignoreCase = true)) continue
                val artifact = catalog.firstOrNull { it.bytes == file.length() } ?: continue
                if (artifact !in found && artifact.verify(file)) found[artifact] = file
            }
        }
        return found
    }
}
