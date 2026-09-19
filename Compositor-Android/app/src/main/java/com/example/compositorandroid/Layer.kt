package com.example.compositorandroid

import android.graphics.Bitmap

class Layer(
    var name: String,
    var bitmap: Bitmap,
    var opacity: Float = 1f,
    var visible: Boolean = true,
    var blend: String = "Normal"
)
