package com.niki914.zafiro.chat.agentic.accessibility

import kotlinx.serialization.json.*

/** Model coordinates are advisory observations, never Android node tokens. */
object GuiOwlObservationCodec {
    fun parse(output: String, width: Int, height: Int): List<VisualTarget> {
        require(width > 0 && height > 0)
        require(output.length <= 32768)
        val nodes = Json.parseToJsonElement(output).jsonObject.getValue("targets").jsonArray
        require(nodes.size <= 32)
        return nodes.map { item ->
            val node = item.jsonObject
            val bounds = node.getValue("bounds").jsonArray.map { it.jsonPrimitive.int }
            require(bounds.size == 4 && bounds.all { it in 0..1000 })
            require(bounds[0] < bounds[2] && bounds[1] < bounds[3])
            val label = node.getValue("label").jsonPrimitive.content
            require(label.isNotBlank() && label.length <= 128)
            val pixels = listOf(bounds[0] * width / 1000, bounds[1] * height / 1000,
                bounds[2] * width / 1000, bounds[3] * height / 1000)
            require(pixels[0] < pixels[2] && pixels[1] < pixels[3])
            VisualTarget(label, pixels, confidence = 0.0, advisoryOnly = true)
        }
    }
}
