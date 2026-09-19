package com.example.compositorandroid;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.EditText;
import java.util.ArrayList;

public class EditorView extends View {
    public enum Tool {
        MOVE, BRUSH, ERASER, RECT, ELLIPSE, TEXT, EYEDROPPER, HAND,
        MARQUEE, LASSO, CROP, GRADIENT, BLUR, SHARPEN, DODGE, BURN
    }

    private final EditorModel model;
    private Tool tool = Tool.BRUSH;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path lasso = new Path();
    private final RectF selection = new RectF();
    private boolean hasSelection = false;
    private boolean drawing = false;
    private float downX, downY, lastX, lastY;
    private float zoom = 0.7f;
    private float offsetX = 0, offsetY = 0;
    private final float brushSize = 24f;

    public EditorView(Context c, EditorModel m) {
        super(c);
        model = m;
        model.ensureBase();
        setBackgroundColor(0xFF111111);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setTool(Tool t) { tool = t; invalidate(); }
    public Tool getTool() { return tool; }
    public float getZoom() { return zoom; }
    public void setZoom(float z) { zoom = Math.max(0.15f, Math.min(4f, z)); invalidate(); }

    public void clearSelection() { hasSelection = false; invalidate(); }
    public void selectAll() { selection.set(0, 0, model.width, model.height); hasSelection = true; invalidate(); }

    private PointF canvasPoint(float x, float y) {
        return new PointF((x - offsetX) / zoom, (y - offsetY) / zoom);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cw = model.width * zoom;
        float ch = model.height * zoom;
        offsetX = (getWidth() - cw) / 2f;
        offsetY = (getHeight() - ch) / 2f;

        c.save();
        c.translate(offsetX, offsetY);
        c.scale(zoom, zoom);

        drawChecker(c, model.width, model.height);

        for (Layer l : model.layers) {
            if (!l.visible) continue;
            paint.setAlpha(Math.round(l.opacity * 255));
            paint.setBlendMode(blendFor(l.blend));
            if (l.mask == null) {
                c.drawBitmap(l.bitmap, 0, 0, paint);
            } else {
                Bitmap masked = applyMask(l.bitmap, l.mask);
                c.drawBitmap(masked, 0, 0, paint);
                masked.recycle();
            }
        }

        paint.setBlendMode(BlendMode.SRC_OVER);
        paint.setAlpha(255);

        if (hasSelection) {
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
            s.setStyle(Paint.Style.STROKE);
            s.setColor(Color.WHITE);
            s.setStrokeWidth(2f / zoom);
            s.setPathEffect(new DashPathEffect(new float[]{8f / zoom, 8f / zoom}, 0));
            if (tool == Tool.LASSO) c.drawPath(lasso, s);
            else c.drawRect(selection, s);
        }

        if (tool == Tool.CROP && drawing) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f / zoom);
            p.setColor(0xFF4EA1FF);
            c.drawRect(selection, p);
            p.setColor(0x55FFFFFF);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(0, 0, model.width, selection.top, p);
            c.drawRect(0, selection.bottom, model.width, model.height, p);
            c.drawRect(0, selection.top, selection.left, selection.bottom, p);
            c.drawRect(selection.right, selection.top, model.width, selection.bottom, p);
        }

        c.restore();
    }

    private BlendMode blendFor(String name) {
        if (Build.VERSION.SDK_INT < 29) return BlendMode.SRC_OVER;
        switch (name) {
            case "Multiply": return BlendMode.MULTIPLY;
            case "Screen": return BlendMode.SCREEN;
            case "Darken": return BlendMode.DARKEN;
            case "Lighten": return BlendMode.LIGHTEN;
            case "Add": return BlendMode.PLUS;
            default: return BlendMode.SRC_OVER;
        }
    }

    private Bitmap applyMask(Bitmap source, Bitmap mask) {
        Bitmap out = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(source, 0, 0, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawBitmap(mask, 0, 0, p);
        p.setXfermode(null);
        return out;
    }

    private void drawChecker(Canvas c, int w, int h) {
        int s = 24;
        Paint p = new Paint();
        for (int y = 0; y < h; y += s) {
            for (int x = 0; x < w; x += s) {
                p.setColor(((x / s + y / s) & 1) == 0 ? 0xFF282828 : 0xFF202020);
                c.drawRect(x, y, x + s, y + s, p);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float sx = e.getX(), sy = e.getY();
        PointF cp = canvasPoint(sx, sy);
        Layer layer = model.layers.get(model.activeLayer);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = cp.x;
                downY = lastY = cp.y;
                drawing = true;

                if (tool == Tool.LASSO) {
                    lasso.reset();
                    lasso.moveTo(cp.x, cp.y);
                    hasSelection = true;
                    return true;
                }

                if (tool == Tool.MARQUEE || tool == Tool.CROP) {
                    selection.set(cp.x, cp.y, cp.x, cp.y);
                    hasSelection = true;
                    invalidate();
                    return true;
                }

                if (tool == Tool.MOVE) return true;

                if (tool == Tool.HAND) return true;

                if (tool == Tool.EYEDROPPER) {
                    int px = Math.max(0, Math.min(model.width - 1, Math.round(cp.x)));
                    int py = Math.max(0, Math.min(model.height - 1, Math.round(cp.y)));
                    for (int i = model.layers.size() - 1; i >= 0; i--) {
                        if (model.layers.get(i).visible) {
                            model.foreground = model.layers.get(i).bitmap.getPixel(px, py);
                            break;
                        }
                    }
                    invalidate();
                    return true;
                }

                if (tool == Tool.BRUSH || tool == Tool.ERASER || tool == Tool.DODGE || tool == Tool.BURN) {
                    model.snapshot();
                    paintAt(layer.bitmap, cp.x, cp.y, cp.x, cp.y);
                    invalidate();
                    return true;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (tool == Tool.HAND) {
                    offsetX += sx - lastX;
                    offsetY += sy - lastY;
                    lastX = sx;
                    lastY = sy;
                    invalidate();
                    return true;
                }

                if (tool == Tool.MOVE) {
                    float dx = cp.x - lastX, dy = cp.y - lastY;
                    layer.bitmap = translateBitmap(layer.bitmap, dx, dy);
                    lastX = cp.x;
                    lastY = cp.y;
                    invalidate();
                    return true;
                }

                if (tool == Tool.LASSO) {
                    lasso.lineTo(cp.x, cp.y);
                    invalidate();
                    return true;
                }

                if (tool == Tool.MARQUEE || tool == Tool.CROP) {
                    selection.set(Math.min(downX, cp.x), Math.min(downY, cp.y),
                            Math.max(downX, cp.x), Math.max(downY, cp.y));
                    invalidate();
                    return true;
                }

                if (tool == Tool.BRUSH || tool == Tool.ERASER || tool == Tool.DODGE || tool == Tool.BURN) {
                    paintAt(layer.bitmap, lastX, lastY, cp.x, cp.y);
                    lastX = cp.x;
                    lastY = cp.y;
                    invalidate();
                    return true;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (tool == Tool.RECT || tool == Tool.ELLIPSE) {
                    model.snapshot();
                    drawShape(layer.bitmap, downX, downY, cp.x, cp.y, tool == Tool.RECT);
                } else if (tool == Tool.TEXT) {
                    showTextDialog(cp.x, cp.y);
                } else if (tool == Tool.CROP && selection.width() > 4 && selection.height() > 4) {
                    cropToSelection();
                } else if (tool == Tool.LASSO) {
                    lasso.close();
                } else if (tool == Tool.MARQUEE) {
                    normalizeSelection();
                } else if (tool == Tool.BLUR || tool == Tool.SHARPEN) {
                    model.snapshot();
                    filterArea(layer.bitmap, tool);
                }
                drawing = false;
                invalidate();
                return true;
        }
        return true;
    }

    private void normalizeSelection() {
        selection.left = Math.max(0, Math.min(model.width, selection.left));
        selection.right = Math.max(0, Math.min(model.width, selection.right));
        selection.top = Math.max(0, Math.min(model.height, selection.top));
        selection.bottom = Math.max(0, Math.min(model.height, selection.bottom));
    }

    private void paintAt(Bitmap b, float x1, float y1, float x2, float y2) {
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeWidth(brushSize);
        p.setStrokeCap(Paint.Cap.ROUND);

        if (tool == Tool.ERASER) {
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        } else if (tool == Tool.DODGE) {
            p.setColor(Color.WHITE);
            p.setAlpha(55);
        } else if (tool == Tool.BURN) {
            p.setColor(Color.BLACK);
            p.setAlpha(55);
        } else {
            p.setColor(model.foreground);
        }
        c.drawLine(x1, y1, x2, y2, p);
    }

    private void drawShape(Bitmap b, float x1, float y1, float x2, float y2, boolean rect) {
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(model.foreground);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(7);

        RectF r = new RectF(Math.min(x1, x2), Math.min(y1, y2),
                Math.max(x1, x2), Math.max(y1, y2));
        if (rect) c.drawRect(r, p); else c.drawOval(r, p);
    }

    public void applyBlur() { model.snapshot(); filterArea(model.layers.get(model.activeLayer).bitmap, Tool.BLUR); invalidate(); }
    public void applySharpen() { model.snapshot(); filterArea(model.layers.get(model.activeLayer).bitmap, Tool.SHARPEN); invalidate(); }

    private void filterArea(Bitmap b, Tool t) {
        Bitmap src = b.copy(Bitmap.Config.ARGB_8888, true);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (t == Tool.BLUR) {
            p.setMaskFilter(new BlurMaskFilter(8, BlurMaskFilter.Blur.NORMAL));
        }
        Canvas c = new Canvas(b);
        RectF r = hasSelection ? selection : new RectF(0,0,b.getWidth(),b.getHeight());
        if (t == Tool.BLUR) {
            c.drawBitmap(src, r, r, p);
        } else {
            p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                    2,-1,0,0,0, -1,2,0,0,0, 0,0,2,-1,0, 0,0,0,1,0
            })));
            c.drawBitmap(src, r, r, p);
        }
        src.recycle();
    }

    public void grayscale() {
        Layer l = model.layers.get(model.activeLayer);
        model.snapshot();
        Bitmap src = l.bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(l.bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        c.drawBitmap(src,0,0,p);
        src.recycle();
        invalidate();
    }

    public void invert() {
        Layer l = model.layers.get(model.activeLayer);
        model.snapshot();
        Bitmap src = l.bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(l.bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                -1,0,0,0,255, 0,-1,0,0,255, 0,0,-1,0,255, 0,0,0,1,0
        })));
        c.drawBitmap(src,0,0,p);
        src.recycle();
        invalidate();
    }

    private void cropToSelection() {
        Rect r = new Rect(Math.round(selection.left), Math.round(selection.top),
                Math.round(selection.right), Math.round(selection.bottom));
        int w = Math.max(1, r.width()), h = Math.max(1, r.height());
        model.snapshot();
        for (Layer l : model.layers) {
            Bitmap out = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            new Canvas(out).drawBitmap(l.bitmap, -r.left, -r.top, new Paint(Paint.ANTI_ALIAS_FLAG));
            l.bitmap.recycle();
            l.bitmap = out;
            if (l.mask != null) {
                Bitmap m = Bitmap.createBitmap(w,h,Bitmap.Config.ALPHA_8);
                new Canvas(m).drawBitmap(l.mask,-r.left,-r.top,new Paint(Paint.ANTI_ALIAS_FLAG));
                l.mask.recycle();
                l.mask=m;
            }
        }
        model.width=w; model.height=h;
        hasSelection=false;
        zoom=Math.min(getWidth()/(float)w,getHeight()/(float)h);
        invalidate();
    }

    public void addMask() {
        Layer l = model.layers.get(model.activeLayer);
        Bitmap mask = Bitmap.createBitmap(model.width, model.height, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(mask);
        c.drawColor(Color.WHITE);
        l.mask = mask;
        invalidate();
    }

    public void removeMask() {
        Layer l = model.layers.get(model.activeLayer);
        if (l.mask != null) { l.mask.recycle(); l.mask=null; invalidate(); }
    }

    private Bitmap translateBitmap(Bitmap src, float dx, float dy) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        c.drawBitmap(src, dx, dy, new Paint(Paint.ANTI_ALIAS_FLAG));
        if (!src.isRecycled()) src.recycle();
        return out;
    }

    private void showTextDialog(float x, float y) {
        final EditText input = new EditText(getContext());
        input.setSingleLine(false);
        input.setHint("Text");

        new AlertDialog.Builder(getContext())
                .setTitle("Text")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Insert", (d, w) -> {
                    model.snapshot();
                    Canvas c = new Canvas(model.layers.get(model.activeLayer).bitmap);
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setColor(model.foreground);
                    p.setTextSize(64);
                    c.drawText(input.getText().toString(), x, y, p);
                    invalidate();
                }).show();
    }
}
