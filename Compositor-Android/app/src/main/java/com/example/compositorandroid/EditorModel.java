package com.example.compositorandroid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.util.ArrayDeque;
import java.util.ArrayList;

public class EditorModel {
    public int width = 1200;
    public int height = 800;
    public final ArrayList<Layer> layers = new ArrayList<>();
    public int activeLayer = 0;
    public int foreground = 0xFFFFFFFF;
    public int background = 0xFF000000;
    public String documentName = "Untitled";

    private final ArrayDeque<ArrayList<Layer>> undo = new ArrayDeque<>();
    private final ArrayDeque<ArrayList<Layer>> redo = new ArrayDeque<>();

    public void ensureBase() {
        if (layers.isEmpty()) {
            Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(0xFF262626);
            layers.add(new Layer("Background", b));
            activeLayer = 0;
            snapshot();
        }
        activeLayer = Math.max(0, Math.min(activeLayer, layers.size() - 1));
    }

    public void newLayer() {
        newLayer("Layer " + (layers.size() + 1));
    }

    public void newLayer(String name) {
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        layers.add(new Layer(name, b));
        activeLayer = layers.size() - 1;
    }

    public void deleteActive() {
        if (layers.size() <= 1) return;
        snapshot();
        layers.remove(activeLayer);
        activeLayer = Math.max(0, Math.min(activeLayer, layers.size() - 1));
    }

    public void duplicateActive() {
        if (layers.isEmpty()) return;
        snapshot();
        layers.add(activeLayer + 1, layers.get(activeLayer).copy());
        activeLayer++;
    }

    public void clearUndo() {
        undo.clear();
        redo.clear();
    }

    public void snapshot() {
        ArrayList<Layer> s = cloneLayers(layers);
        undo.push(s);
        while (undo.size() > 15) undo.removeLast();
        redo.clear();
    }

    public boolean undo() {
        if (undo.size() <= 1) return false;
        ArrayList<Layer> current = cloneLayers(layers);
        redo.push(current);
        undo.pop();
        restoreFrom(undo.peek());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        ArrayList<Layer> s = redo.pop();
        undo.push(cloneLayers(s));
        restoreFrom(s);
        return true;
    }

    private void restoreFrom(ArrayList<Layer> source) {
        layers.clear();
        for (Layer l : source) {
            Layer copy = new Layer(l.name, l.bitmap.copy(Bitmap.Config.ARGB_8888, true), l.opacity, l.visible, l.blend);
            if (l.mask != null) copy.mask = l.mask.copy(Bitmap.Config.ALPHA_8, true);
            layers.add(copy);
        }
        activeLayer = Math.max(0, Math.min(activeLayer, layers.size() - 1));
    }

    private ArrayList<Layer> cloneLayers(ArrayList<Layer> source) {
        ArrayList<Layer> out = new ArrayList<>();
        for (Layer l : source) {
            Layer copy = new Layer(l.name, l.bitmap.copy(Bitmap.Config.ARGB_8888, true), l.opacity, l.visible, l.blend);
            if (l.mask != null) copy.mask = l.mask.copy(Bitmap.Config.ALPHA_8, true);
            out.add(copy);
        }
        return out;
    }
}
