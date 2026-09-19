package com.example.compositorandroid

import android.graphics.Bitmap

class EditorModel {
    var width = 1200
    var height = 800
    val layers = mutableListOf<Layer>()
    var activeLayer = 0
    var foreground = 0xFFFFFFFF.toInt()
    var background = 0xFF000000.toInt()

    fun ensureBase() {
        if (layers.isEmpty()) {
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            c.drawColor(0xFF222222.toInt())
            layers.add(Layer("Background", b))
        }
        activeLayer = activeLayer.coerceIn(0, layers.lastIndex)
    }

    fun newLayer(name: String = "Layer " + (layers.size + 1)) {
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        layers.add(Layer(name, b))
        activeLayer = layers.lastIndex
    }

    fun deleteActive() {
        if (layers.size <= 1) return
        layers.removeAt(activeLayer)
        activeLayer = activeLayer.coerceIn(0, layers.lastIndex)
    }
}
