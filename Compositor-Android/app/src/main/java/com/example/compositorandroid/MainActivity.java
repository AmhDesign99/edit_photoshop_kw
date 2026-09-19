package com.example.compositorandroid;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    EditorModel model;
    EditorView editor;
    LinearLayout layersList;
    TextView status;
    SeekBar opacity;
    Spinner blend;

    final int panel = Color.rgb(36,36,36);
    final int panel2 = Color.rgb(45,45,45);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        model = new EditorModel();
        model.ensureBase();
        buildUi();
    }

    TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(12);
        t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(10,0,10,0);
        return t;
    }

    TextView button(String s) {
        TextView t = label(s);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(panel2);
        return t;
    }

    void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(25,25,25));

        LinearLayout menus = new LinearLayout(this);
        menus.setBackgroundColor(Color.rgb(30,30,30));
        String[] menuNames = {"File","Edit","Image","Layer","Select","Filter","View","Help"};
        for (String m : menuNames) {
            TextView x = button(m);
            menus.addView(x, new LinearLayout.LayoutParams(0,48,1));
            x.setOnClickListener(v -> showMenu(m,x));
        }
        root.addView(menus, new LinearLayout.LayoutParams(-1,48));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setBackgroundColor(panel);
        String[] toolNames = {"Move","Brush","Eraser","Marquee","Lasso","Crop","Rect","Ellipse","Text","Pick","Gradient","Blur","Sharpen","Dodge","Burn","Hand","+Layer","-Layer"};
        for (String name : toolNames) {
            TextView x=button(name);
            tools.addView(x,new LinearLayout.LayoutParams(96,48));
            x.setOnClickListener(v -> chooseTool(name));
        }
        ScrollView toolScroll = new ScrollView(this);
        toolScroll.addView(tools);
        body.addView(toolScroll,new LinearLayout.LayoutParams(100,-1));

        FrameLayout center = new FrameLayout(this);
        editor=new EditorView(this,model);
        center.addView(editor,new FrameLayout.LayoutParams(-1,-1));

        status=label("70%  | 1200 × 800");
        status.setBackgroundColor(Color.rgb(18,18,18));
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,34,Gravity.BOTTOM);
        center.addView(status,sp);
        body.addView(center,new LinearLayout.LayoutParams(0,-1,1));

        LinearLayout right=new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setBackgroundColor(panel);
        right.addView(label("LAYERS"),new LinearLayout.LayoutParams(260,42));

        opacity=new SeekBar(this);
        opacity.setMax(100); opacity.setProgress(100);
        right.addView(opacity,new LinearLayout.LayoutParams(260,42));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean f){
                if(!model.layers.isEmpty()){
                    model.layers.get(model.activeLayer).opacity=p/100f;
                    editor.invalidate();
                }
            }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){model.snapshot();}
        });

        blend=new Spinner(this);
        String[] blends={"Normal","Multiply","Screen","Darken","Lighten","Add"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,blends);
        blend.setAdapter(adapter);
        right.addView(blend,new LinearLayout.LayoutParams(260,40));
        blend.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(model.layers.size()>0) model.layers.get(model.activeLayer).blend=blends[pos];
                editor.invalidate();
            }
        });

        layersList=new LinearLayout(this);
        layersList.setOrientation(LinearLayout.VERTICAL);
        ScrollView layerScroll=new ScrollView(this);
        layerScroll.addView(layersList);
        right.addView(layerScroll,new LinearLayout.LayoutParams(260,0,1));
        body.addView(right,new LinearLayout.LayoutParams(260,-1));

        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        refreshLayers();
    }

    void chooseTool(String name) {
        if(name.equals("+Layer")) { model.snapshot(); model.newLayer(); refreshLayers(); return; }
        if(name.equals("-Layer")) { model.deleteActive(); refreshLayers(); return; }

        EditorView.Tool t;
        switch(name) {
            case "Move": t=EditorView.Tool.MOVE; break;
            case "Brush": t=EditorView.Tool.BRUSH; break;
            case "Eraser": t=EditorView.Tool.ERASER; break;
            case "Marquee": t=EditorView.Tool.MARQUEE; break;
            case "Lasso": t=EditorView.Tool.LASSO; break;
            case "Crop": t=EditorView.Tool.CROP; break;
            case "Rect": t=EditorView.Tool.RECT; break;
            case "Ellipse": t=EditorView.Tool.ELLIPSE; break;
            case "Text": t=EditorView.Tool.TEXT; break;
            case "Pick": t=EditorView.Tool.EYEDROPPER; break;
            case "Gradient": t=EditorView.Tool.GRADIENT; break;
            case "Blur": t=EditorView.Tool.BLUR; break;
            case "Sharpen": t=EditorView.Tool.SHARPEN; break;
            case "Dodge": t=EditorView.Tool.DODGE; break;
            case "Burn": t=EditorView.Tool.BURN; break;
            default: t=EditorView.Tool.HAND;
        }
        editor.setTool(t);
    }

    void refreshLayers() {
        layersList.removeAllViews();
        for(int i=model.layers.size()-1;i>=0;i--){
            final int idx=i;
            TextView row=label((idx==model.activeLayer?"● ":"○ ")+model.layers.get(idx).name);
            row.setBackgroundColor(idx==model.activeLayer?Color.rgb(52,69,92):panel);
            row.setOnClickListener(v -> {
                model.activeLayer=idx;
                opacity.setProgress(Math.round(model.layers.get(idx).opacity*100));
                blend.setSelection(new String[]{"Normal","Multiply","Screen","Darken","Lighten","Add"}.length-1);
                refreshLayers();
            });
            layersList.addView(row,new LinearLayout.LayoutParams(-1,46));
        }
        updateStatus();
    }

    void showMenu(String s, View anchor) {
        PopupMenu pm=new PopupMenu(this,anchor);
        if(s.equals("File")){
            pm.getMenu().add("Open Image");
            pm.getMenu().add("Export PNG");
            pm.getMenu().add("Export JPG");
        } else if(s.equals("Edit")){
            pm.getMenu().add("Undo");
            pm.getMenu().add("Redo");
            pm.getMenu().add("Select All");
            pm.getMenu().add("Deselect");
        } else if(s.equals("Image")){
            pm.getMenu().add("Grayscale");
            pm.getMenu().add("Invert");
            pm.getMenu().add("Fit");
        } else if(s.equals("Layer")){
            pm.getMenu().add("New Layer");
            pm.getMenu().add("Duplicate Layer");
            pm.getMenu().add("Delete Layer");
            pm.getMenu().add("Add Mask");
            pm.getMenu().add("Remove Mask");
        } else if(s.equals("Select")){
            pm.getMenu().add("Select All");
            pm.getMenu().add("Deselect");
        } else if(s.equals("Filter")){
            pm.getMenu().add("Blur");
            pm.getMenu().add("Sharpen");
            pm.getMenu().add("Grayscale");
            pm.getMenu().add("Invert");
        } else if(s.equals("View")){
            pm.getMenu().add("Zoom In");
            pm.getMenu().add("Zoom Out");
            pm.getMenu().add("Fit");
        } else {
            pm.getMenu().add("Compositor Android");
            pm.getMenu().add("Keyboard / gesture workflow");
        }
        pm.setOnMenuItemClickListener(it->{handleMenu(it.getTitle().toString());return true;});
        pm.show();
    }

    void handleMenu(String a){
        switch(a){
            case "Open Image":
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(i,10); break;
            case "Export PNG": export(Bitmap.CompressFormat.PNG,"png"); break;
            case "Export JPG": export(Bitmap.CompressFormat.JPEG,"jpg"); break;
            case "Undo": if(model.undo()) {refreshLayers();editor.invalidate();} break;
            case "Redo": if(model.redo()) {refreshLayers();editor.invalidate();} break;
            case "Select All": editor.selectAll(); break;
            case "Deselect": editor.clearSelection(); break;
            case "Grayscale": editor.grayscale(); break;
            case "Invert": editor.invert(); break;
            case "Blur": editor.applyBlur(); break;
            case "Sharpen": editor.applySharpen(); break;
            case "New Layer": model.snapshot(); model.newLayer(); refreshLayers(); break;
            case "Duplicate Layer": model.duplicateActive(); refreshLayers(); break;
            case "Delete Layer": model.deleteActive(); refreshLayers(); break;
            case "Add Mask": editor.addMask(); break;
            case "Remove Mask": editor.removeMask(); break;
            case "Zoom In": editor.setZoom(editor.getZoom()*1.2f); updateStatus(); break;
            case "Zoom Out": editor.setZoom(editor.getZoom()/1.2f); updateStatus(); break;
            case "Fit": editor.setZoom(0.7f); updateStatus(); break;
        }
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==10&&c==RESULT_OK&&d!=null&&d.getData()!=null){
            try{
                InputStream in=getContentResolver().openInputStream(d.getData());
                Bitmap src=BitmapFactory.decodeStream(in);
                if(in!=null)in.close();
                if(src==null)throw new IOException("Invalid image");
                model.width=src.getWidth(); model.height=src.getHeight();
                model.layers.clear();
                model.layers.add(new Layer("Image",src.copy(Bitmap.Config.ARGB_8888,true)));
                model.activeLayer=0;
                model.clearUndo(); model.snapshot();
                refreshLayers(); editor.invalidate();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }

    void export(Bitmap.CompressFormat fmt,String ext){
        try{
            Bitmap out=Bitmap.createBitmap(model.width,model.height,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(out);
            c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            for(Layer l:model.layers){
                if(!l.visible)continue;
                p.setAlpha((int)(255*l.opacity));
                c.drawBitmap(l.bitmap,0,0,p);
            }
            String name="compositor_"+System.currentTimeMillis()+"."+ext;
            android.content.ContentValues cv=new android.content.ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME,name);
            cv.put(MediaStore.Images.Media.MIME_TYPE,fmt==Bitmap.CompressFormat.JPEG?"image/jpeg":"image/png");
            android.net.Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
            if(u==null)throw new IOException("Cannot create output");
            OutputStream os=getContentResolver().openOutputStream(u);
            out.compress(fmt,95,os); os.close();
            Toast.makeText(this,"Exported: "+name,Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();}
    }

    void updateStatus(){
        if(status!=null)status.setText(Math.round(editor.getZoom()*100)+"%  |  "+model.width+" × "+model.height);
    }
}
