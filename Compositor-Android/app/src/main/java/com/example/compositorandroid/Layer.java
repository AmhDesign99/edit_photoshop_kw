package com.example.compositorandroid;

import android.graphics.Bitmap;
import android.graphics.PorterDuff;

public class Layer {
    public String name;
    public Bitmap bitmap;
    public Bitmap mask;
    public float opacity = 1f;
    public boolean visible = true;
    public String blend = "Normal";

    public Layer(String name, Bitmap bitmap) {
        this.name = name;
        this.bitmap = bitmap;
    }

    public Layer(String name, Bitmap bitmap, float opacity, boolean visible, String blend) {
        this.name = name;
        this.bitmap = bitmap;
        this.opacity = opacity;
        this.visible = visible;
        this.blend = blend;
    }

    public Layer copy() {
        Bitmap b = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Layer out = new Layer(name + " copy", b, opacity, visible, blend);
        if (mask != null) out.mask = mask.copy(Bitmap.Config.ALPHA_8, true);
        return out;
    }

    public int blendMode() {
        switch (blend) {
            case "Multiply": return PorterDuff.Mode.MULTIPLY.ordinal();
            case "Screen": return PorterDuff.Mode.SCREEN.ordinal();
            case "Darken": return PorterDuff.Mode.DARKEN.ordinal();
            case "Lighten": return PorterDuff.Mode.LIGHTEN.ordinal();
            case "Add": return PorterDuff.Mode.ADD.ordinal();
            default: return PorterDuff.Mode.SRC_OVER.ordinal();
        }
    }
}
