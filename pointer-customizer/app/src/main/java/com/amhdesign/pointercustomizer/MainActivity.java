package com.amhdesign.pointercustomizer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String POINTER_SCALE = "pointer_scale";
    private TextView status;
    private TextView scaleText;
    private PointerPreviewView preview;
    private SeekBar seekBarCache;
    private float pendingScale = 1.0f;

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(dp(4),dp(8),dp(4),dp(8));return t;}

    @Override public void onCreate(Bundle state){super.onCreate(state);buildUi();}
    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(18),dp(20),dp(18)); root.setBackgroundColor(Color.rgb(17,19,24));
        TextView title=label("Pointer Customizer",26); title.setTypeface(null,android.graphics.Typeface.BOLD); root.addView(title);
        TextView subtitle=label("Ukuran pointer sistem Android 14+",14); subtitle.setTextColor(Color.LTGRAY); root.addView(subtitle);
        preview=new PointerPreviewView(this); root.addView(preview,new LinearLayout.LayoutParams(-1,dp(170)));
        root.addView(label("Ukuran pointer",17)); scaleText=label("100%",15); root.addView(scaleText);
        SeekBar seek=new SeekBar(this); seekBarCache=seek; seek.setMax(150); seek.setProgress(50); root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){pendingScale=.5f+(p/100f);scaleText.setText(String.format(Locale.US,"%.0f%%",pendingScale*100f));preview.setScale(pendingScale);}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        root.addView(label("Bentuk preview",17));
        Spinner shapes=new Spinner(this); String[] names={"Arrow","Circle","Crosshair","Dot"}; shapes.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names)); root.addView(shapes);
        shapes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){preview.setPointerType(pos);}});
        Button permission=new Button(this); permission.setText("Izinkan ubah pengaturan sistem"); root.addView(permission); permission.setOnClickListener(v->openWriteSettings());
        Button apply=new Button(this); apply.setText("Terapkan ukuran pointer"); root.addView(apply); apply.setOnClickListener(v->applyScale());
        Button reset=new Button(this); reset.setText("Kembalikan ke 100%"); root.addView(reset); reset.setOnClickListener(v->{pendingScale=1f;seekReset();applyScale();});
        Button access=new Button(this); access.setText("Buka Accessibility"); root.addView(access); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        status=label("",13); status.setTextColor(Color.LTGRAY); root.addView(status);
        TextView note=label("Bentuk pada panel adalah preview. Android tidak menyediakan API publik untuk mengganti native cursor menjadi PNG custom secara global.",12); note.setTextColor(Color.GRAY); root.addView(note);
        setContentView(root); refreshState();
    }
    private void seekReset(){if(seekBarCache!=null)seekBarCache.setProgress(50);}
    @Override protected void onResume(){super.onResume();if(status!=null)refreshState();}
    private void refreshState(){boolean canWrite=Build.VERSION.SDK_INT<23||Settings.System.canWrite(this);float current=Settings.System.getFloat(getContentResolver(),POINTER_SCALE,1f);pendingScale=current;preview.setScale(current);scaleText.setText(String.format(Locale.US,"%.0f%%",current*100f));status.setText(canWrite?"Izin sistem: aktif • Skala: "+String.format(Locale.US,"%.0f%%",current*100f):"Izin sistem: belum diberikan");}
    private void openWriteSettings(){if(Build.VERSION.SDK_INT>=23){try{startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}}
    private void applyScale(){if(Build.VERSION.SDK_INT>=23&&!Settings.System.canWrite(this)){Toast.makeText(this,"Berikan izin ubah pengaturan sistem terlebih dahulu.",Toast.LENGTH_LONG).show();openWriteSettings();return;}float value=Math.max(1f,Math.min(2f,pendingScale));try{boolean ok=Settings.System.putFloat(getContentResolver(),POINTER_SCALE,value);Toast.makeText(this,ok?"Pointer "+(int)(value*100)+"%":"Firmware menolak pointer_scale",Toast.LENGTH_LONG).show();refreshState();}catch(SecurityException e){Toast.makeText(this,"Akses WRITE_SETTINGS ditolak.",Toast.LENGTH_LONG).show();}}
}
