package com.example.compositorandroid;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

public class EditorView extends View {
    public enum Tool { MOVE, BRUSH, ERASER, RECT, ELLIPSE, TEXT, EYEDROPPER, HAND }

    private final EditorModel model;
    private Tool tool = Tool.BRUSH;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private float downX, downY, lastX, lastY;
    private boolean drawing = false;
    private float zoom = 1f;
    private float offsetX = 0, offsetY = 0;

    public EditorView(Context c, EditorModel m) {
        super(c);
        model=m;
        model.ensureBase();
        setBackgroundColor(0xFF171717);
        setFocusable(true);
    }

    public void setTool(Tool t) { tool=t; }
    public Tool getTool(){ return tool; }
    public float getZoom(){ return zoom; }

    public void setZoom(float z){
        zoom=Math.max(.1f,Math.min(4f,z));
        invalidate();
    }

    private PointF canvasPoint(float x,float y){
        return new PointF((x-offsetX)/zoom,(y-offsetY)/zoom);
    }

    @Override
    protected void onDraw(Canvas c){
        super.onDraw(c);

        float cw=model.width*zoom;
        float ch=model.height*zoom;
        offsetX=(getWidth()-cw)/2f;
        offsetY=(getHeight()-ch)/2f;

        c.save();
        c.translate(offsetX,offsetY);
        c.scale(zoom,zoom);

        drawChecker(c, model.width, model.height);

        for(Layer l:model.layers){
            if(!l.visible) continue;
            paint.setAlpha(Math.round(l.opacity*255));
            c.drawBitmap(l.bitmap,0,0,paint);
        }

        paint.setAlpha(255);

        Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f/zoom);
        border.setColor(0xFF6B6B6B);
        c.drawRect(0,0,model.width,model.height,border);

        c.restore();
    }

    private void drawChecker(Canvas c,int w,int h){
        int s=24;
        Paint p=new Paint();

        for(int y=0;y<h;y+=s){
            for(int x=0;x<w;x+=s){
                p.setColor(((x/s+y/s)&1)==0?0xFF262626:0xFF202020);
                c.drawRect(x,y,x+s,y+s,p);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e){
        float x=e.getX();
        float y=e.getY();
        PointF cp=canvasPoint(x,y);

        if(model.layers.isEmpty()) return true;

        Layer layer=model.layers.get(model.activeLayer);

        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=lastX=cp.x;
                downY=lastY=cp.y;
                drawing=true;

                if(tool==Tool.MOVE || tool==Tool.HAND){
                    return true;
                }

                if(tool==Tool.EYEDROPPER){
                    int px=Math.max(0,Math.min(model.width-1,Math.round(cp.x)));
                    int py=Math.max(0,Math.min(model.height-1,Math.round(cp.y)));

                    for(int i=model.layers.size()-1;i>=0;i--){
                        if(model.layers.get(i).visible){
                            model.foreground=model.layers.get(i).bitmap.getPixel(px,py);
                            break;
                        }
                    }
                    invalidate();
                    return true;
                }

                if(tool==Tool.BRUSH || tool==Tool.ERASER){
                    drawStroke(layer.bitmap,cp.x,cp.y,cp.x,cp.y);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if(tool==Tool.MOVE){
                    float dx=cp.x-lastX;
                    float dy=cp.y-lastY;
                    layer.bitmap=translateBitmap(layer.bitmap,dx,dy);
                    lastX=cp.x;
                    lastY=cp.y;
                    invalidate();
                    return true;
                }

                if(tool==Tool.HAND){
                    float dx=x-lastX;
                    float dy=y-lastY;
                    offsetX+=dx;
                    offsetY+=dy;
                    lastX=x;
                    lastY=y;
                    invalidate();
                    return true;
                }

                if(tool==Tool.BRUSH || tool==Tool.ERASER){
                    drawStroke(layer.bitmap,lastX,lastY,cp.x,cp.y);
                    lastX=cp.x;
                    lastY=cp.y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if(tool==Tool.RECT || tool==Tool.ELLIPSE){
                    drawShape(layer.bitmap,downX,downY,cp.x,cp.y,tool==Tool.RECT);
                    invalidate();
                }

                if(tool==Tool.TEXT){
                    showTextDialog(cp.x,cp.y);
                }

                drawing=false;
                return true;
        }

        return true;
    }

    private void drawStroke(Bitmap b,float x1,float y1,float x2,float y2){
        Canvas c=new Canvas(b);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

        p.setColor(model.foreground);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(18);
        p.setStrokeCap(Paint.Cap.ROUND);

        if(tool==Tool.ERASER){
            p.setColor(Color.TRANSPARENT);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        c.drawLine(x1,y1,x2,y2,p);
    }

    private void drawShape(Bitmap b,float x1,float y1,float x2,float y2,boolean rect){
        Canvas c=new Canvas(b);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

        p.setColor(model.foreground);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8);

        RectF r=new RectF(
            Math.min(x1,x2),Math.min(y1,y2),
            Math.max(x1,x2),Math.max(y1,y2)
        );

        if(rect) c.drawRect(r,p);
        else c.drawOval(r,p);
    }

    private Bitmap translateBitmap(Bitmap src,float dx,float dy){
        Bitmap out=Bitmap.createBitmap(
            src.getWidth(),
            src.getHeight(),
            Bitmap.Config.ARGB_8888
        );

        Canvas c=new Canvas(out);
        c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);

        c.drawBitmap(src,dx,dy,new Paint(Paint.ANTI_ALIAS_FLAG));
        if(!src.isRecycled()) src.recycle();

        return out;
    }

    private void showTextDialog(float x,float y){
        final android.widget.EditText input=new android.widget.EditText(getContext());
        input.setSingleLine(false);
        input.setHint("Text");

        new android.app.AlertDialog.Builder(getContext())
            .setTitle("Text")
            .setView(input)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Insert",(d,w)->{
                Canvas c=new Canvas(model.layers.get(model.activeLayer).bitmap);
                Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(model.foreground);
                p.setTextSize(64);
                c.drawText(input.getText().toString(),x,y,p);
                invalidate();
            }).show();
    }
}
