package com.amhdesign.pointercustomizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class PointerPreviewView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float scale = 1f;
    private int pointerColor = Color.WHITE;
    private int pointerType = 0;

    public PointerPreviewView(Context context) { super(context); init(); }
    public PointerPreviewView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    private void init() { setLayerType(View.LAYER_TYPE_SOFTWARE, null); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(2f); stroke.setColor(Color.BLACK); fill.setStyle(Paint.Style.FILL); setBackgroundColor(Color.rgb(30,34,42)); }
    public void setScale(float scale) { this.scale = scale; invalidate(); }
    public void setPointerColor(int color) { this.pointerColor = color; invalidate(); }
    public void setPointerType(int type) { this.pointerType = type; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); float cx=getWidth()/2f, cy=getHeight()/2f, s=28f*scale;
        fill.setColor(pointerColor); fill.setShadowLayer(4f*scale,2f*scale,2f*scale,Color.BLACK);
        stroke.setColor(pointerColor==Color.BLACK?Color.WHITE:Color.BLACK); stroke.setStrokeWidth(Math.max(2f,2f*scale));
        if(pointerType==0){ Path p=new Path(); p.moveTo(cx-s*.55f,cy-s*.9f); p.lineTo(cx-s*.05f,cy+s*.65f); p.lineTo(cx+s*.20f,cy+s*.12f); p.lineTo(cx+s*.70f,cy+s*.72f); p.lineTo(cx+s*.98f,cy+s*.43f); p.lineTo(cx+s*.48f,cy-s*.15f); p.lineTo(cx+s*.95f,cy-s*.28f); p.close(); canvas.drawPath(p,fill); canvas.drawPath(p,stroke); }
        else if(pointerType==1){ canvas.drawCircle(cx,cy,s*.65f,fill); canvas.drawCircle(cx,cy,s*.65f,stroke); }
        else if(pointerType==2){ canvas.drawLine(cx-s,cy,cx+s,cy,stroke); canvas.drawLine(cx,cy-s,cx,cy+s,stroke); canvas.drawCircle(cx,cy,s*.18f,fill); }
        else { canvas.drawCircle(cx,cy,s*.30f,fill); }
        fill.clearShadowLayer();
    }
}
