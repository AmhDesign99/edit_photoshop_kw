package com.example.compositorandroid;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    EditorModel model;
    EditorView editor;
    LinearLayout root, layersList;
    TextView status;
    SeekBar opacity;

    final int blue = Color.rgb(47,124,246);
    final int panel = Color.rgb(36,36,36);

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        model = new EditorModel();
        model.ensureBase();
        buildUi();
    }

    TextView tv(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setPadding(14,8,14,8);
        return t;
    }

    void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(25,25,25));

        LinearLayout menu = new LinearLayout(this);
        menu.setBackgroundColor(Color.rgb(30,30,30));
        String[] ms = {"File","Edit","Image","Layer","Select","Filter","View","Help"};

        for (String s : ms) {
            TextView m = tv(s);
            menu.addView(m, new LinearLayout.LayoutParams(0,48,1));
            m.setOnClickListener(v -> showMenu(s,m));
        }
        root.addView(menu, new LinearLayout.LayoutParams(-1,48));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setBackgroundColor(panel);

        String[] ts = {"Move","Brush","Eraser","Rect","Ellipse","Text","Pick","Hand","+ Layer","Del"};
        for (String s : ts) {
            TextView b = tv(s);
            tools.addView(b, new LinearLayout.LayoutParams(84,52));
            b.setOnClickListener(v -> tool(s));
        }
        body.addView(tools,new LinearLayout.LayoutParams(92,-1));

        FrameLayout center = new FrameLayout(this);
        editor = new EditorView(this,model);
        center.addView(editor,new FrameLayout.LayoutParams(-1,-1));

        status = tv("100%  |  1200 × 800");
        status.setBackgroundColor(Color.rgb(18,18,18));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1,34,Gravity.BOTTOM);
        center.addView(status,sp);
        body.addView(center,new LinearLayout.LayoutParams(0,-1,1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setBackgroundColor(panel);

        TextView title = tv("LAYERS");
        title.setTextSize(13);
        right.addView(title,new LinearLayout.LayoutParams(260,44));

        opacity = new SeekBar(this);
        opacity.setMax(100);
        opacity.setProgress(100);
        right.addView(opacity,new LinearLayout.LayoutParams(260,44));

        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int p,boolean f) {
                if (!model.layers.isEmpty()) {
                    model.layers.get(model.activeLayer).opacity=p/100f;
                    editor.invalidate();
                }
            }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });

        layersList = new LinearLayout(this);
        layersList.setOrientation(LinearLayout.VERTICAL);
        right.addView(layersList,new LinearLayout.LayoutParams(260,0,1));

        body.addView(right,new LinearLayout.LayoutParams(260,-1));
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));

        setContentView(root);
        refreshLayers();
    }

    void tool(String s) {
        if (s.equals("+ Layer")) {
            model.newLayer();
            refreshLayers();
            return;
        }
        if (s.equals("Del")) {
            model.deleteActive();
            refreshLayers();
            return;
        }

        EditorView.Tool t = EditorView.Tool.BRUSH;
        switch (s) {
            case "Move": t=EditorView.Tool.MOVE; break;
            case "Brush": t=EditorView.Tool.BRUSH; break;
            case "Eraser": t=EditorView.Tool.ERASER; break;
            case "Rect": t=EditorView.Tool.RECT; break;
            case "Ellipse": t=EditorView.Tool.ELLIPSE; break;
            case "Text": t=EditorView.Tool.TEXT; break;
            case "Pick": t=EditorView.Tool.EYEDROPPER; break;
            case "Hand": t=EditorView.Tool.HAND; break;
        }
        editor.setTool(t);
    }

    void refreshLayers() {
        layersList.removeAllViews();

        for (int i=model.layers.size()-1;i>=0;i--) {
            final int idx=i;
            TextView l=tv((idx==model.activeLayer?"● ":"")+model.layers.get(idx).name);
            l.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            l.setBackgroundColor(idx==model.activeLayer?Color.rgb(52,69,92):panel);
            l.setOnClickListener(v -> {
                model.activeLayer=idx;
                opacity.setProgress(Math.round(model.layers.get(idx).opacity*100));
                refreshLayers();
            });
            layersList.addView(l,new LinearLayout.LayoutParams(-1,48));
        }
    }

    void showMenu(String s,View anchor) {
        PopupMenu pm=new PopupMenu(this,anchor);

        if(s.equals("File")){
            pm.getMenu().add("Open Image");
            pm.getMenu().add("Export PNG");
            pm.getMenu().add("Export JPG");
        } else if(s.equals("Layer")){
            pm.getMenu().add("New Layer");
            pm.getMenu().add("Delete Layer");
            pm.getMenu().add("Duplicate Layer");
        } else if(s.equals("View")){
            pm.getMenu().add("Zoom In");
            pm.getMenu().add("Zoom Out");
            pm.getMenu().add("Fit");
        } else if(s.equals("Edit")){
            pm.getMenu().add("Undo");
            pm.getMenu().add("Redo");
        } else if(s.equals("Image")){
            pm.getMenu().add("Reset View");
        } else {
            pm.getMenu().add("Coming soon");
        }

        pm.setOnMenuItemClickListener(it -> {
            handleMenu(s,it.getTitle().toString());
            return true;
        });
        pm.show();
    }

    void handleMenu(String s,String a) {
        if(a.equals("Open Image")){
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i,10);
        } else if(a.equals("Export PNG")){
            export(Bitmap.CompressFormat.PNG,"png");
        } else if(a.equals("Export JPG")){
            export(Bitmap.CompressFormat.JPEG,"jpg");
        } else if(a.equals("New Layer")){
            model.newLayer();
            refreshLayers();
        } else if(a.equals("Delete Layer")){
            model.deleteActive();
            refreshLayers();
        } else if(a.equals("Duplicate Layer")){
            duplicateActiveLayer();
        } else if(a.equals("Zoom In")){
            editor.setZoom(editor.getZoom()*1.2f);
            updateStatus();
        } else if(a.equals("Zoom Out")){
            editor.setZoom(editor.getZoom()/1.2f);
            updateStatus();
        } else if(a.equals("Fit")){
            editor.setZoom(1f);
            updateStatus();
        } else if(a.equals("Reset View")){
            editor.setZoom(1f);
            updateStatus();
        }
    }

    void duplicateActiveLayer() {
        if (model.layers.isEmpty()) return;
        Layer src=model.layers.get(model.activeLayer);
        Bitmap copy=src.bitmap.copy(Bitmap.Config.ARGB_8888,true);
        model.layers.add(new Layer(src.name+" copy",copy,src.opacity,src.visible,src.blend));
        model.activeLayer=model.layers.size()-1;
        refreshLayers();
    }

    @Override
    protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==10&&c==RESULT_OK&&d!=null&&d.getData()!=null){
            try{
                Uri uri=d.getData();
                InputStream in=getContentResolver().openInputStream(uri);
                Bitmap src=BitmapFactory.decodeStream(in);
                if(in!=null) in.close();
                if(src==null)return;

                model.width=src.getWidth();
                model.height=src.getHeight();
                model.layers.clear();
                model.layers.add(new Layer("Image",src.copy(Bitmap.Config.ARGB_8888,true)));
                model.activeLayer=0;
                refreshLayers();
                editor.invalidate();
                updateStatus();
            }catch(Exception e){
                Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
            }
        }
    }

    void export(Bitmap.CompressFormat fmt,String ext){
        try{
            Bitmap out=Bitmap.createBitmap(model.width,model.height,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(out);
            c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);

            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            for(Layer l:model.layers){
                if(!l.visible) continue;
                p.setAlpha((int)(255*l.opacity));
                c.drawBitmap(l.bitmap,0,0,p);
            }

            String name="compositor_export_"+System.currentTimeMillis()+"."+ext;
            ContentValues cv=new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME,name);
            cv.put(MediaStore.Images.Media.MIME_TYPE,fmt==Bitmap.CompressFormat.JPEG?"image/jpeg":"image/png");

            Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
            if(u==null) throw new IOException("Tidak dapat membuat file output.");

            OutputStream os=getContentResolver().openOutputStream(u);
            if(os==null) throw new IOException("Tidak dapat membuka output stream.");
            out.compress(fmt,95,os);
            os.close();

            Toast.makeText(this,"Exported to Pictures",Toast.LENGTH_SHORT).show();
        }catch(Exception e){
            Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();
        }
    }

    void updateStatus(){
        status.setText(Math.round(editor.getZoom()*100)+"%  |  "+model.width+" × "+model.height);
    }
}
