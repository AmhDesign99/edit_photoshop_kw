package com.amhdesign.pointercustomizer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
    private static final String PREFS = "pointer_prefs";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_TYPE = "type";

    private TextView status;
    private TextView scaleText;
    private PointerPreviewView preview;
    private SeekBar seekBar;
    private Spinner shapes;
    private SharedPreferences prefs;
    private float pendingScale = 1.0f;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView label(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        t.setPadding(dp(4), dp(8), dp(4), dp(8));
        return t;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        loadPreset();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackgroundColor(Color.rgb(17, 19, 24));

        TextView title = label("Pointer Customizer", 26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = label("Pengaturan pointer pribadi untuk Android 14+", 14);
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        preview = new PointerPreviewView(this);
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(170)));

        root.addView(label("Ukuran preview", 17));
        scaleText = label("100%", 15);
        root.addView(scaleText);

        seekBar = new SeekBar(this);
        seekBar.setMax(475);
        root.addView(seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                pendingScale = 0.25f + (p / 100f);
                updatePreview();
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });

        root.addView(label("Bentuk preview", 17));
        shapes = new Spinner(this);
        String[] names = {"Arrow", "Circle", "Crosshair", "Dot"};
        shapes.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        root.addView(shapes);
        shapes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                preview.setPointerType(pos);
                prefs.edit().putInt(KEY_TYPE, pos).apply();
                updateStatus();
            }
        });

        Button save = new Button(this);
        save.setText("Simpan preset");
        root.addView(save);
        save.setOnClickListener(v -> {
            prefs.edit().putFloat(KEY_SCALE, pendingScale).apply();
            Toast.makeText(this, "Preset tersimpan", Toast.LENGTH_SHORT).show();
            updateStatus();
        });

        Button access = new Button(this);
        access.setText("Buka Pengaturan Aksesibilitas");
        root.addView(access);
        access.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });

        Button reset = new Button(this);
        reset.setText("Kembalikan preview ke 100%");
        root.addView(reset);
        reset.setOnClickListener(v -> {
            pendingScale = 1f;
            seekBar.setProgress(75);
            shapes.setSelection(0);
            prefs.edit().putFloat(KEY_SCALE, 1f).putInt(KEY_TYPE, 0).apply();
            updatePreview();
            Toast.makeText(this, "Preview dikembalikan ke 100%", Toast.LENGTH_SHORT).show();
        });

        status = label("", 13);
        status.setTextColor(Color.LTGRAY);
        root.addView(status);

        TextView note = label(
            "Catatan: Android/HyperOS tidak menyediakan API publik untuk mengganti native cursor custom secara global. Slider ini mengatur preview lokal; pengaturan sistem dilakukan melalui halaman Aksesibilitas perangkat.",
            12
        );
        note.setTextColor(Color.GRAY);
        root.addView(note);

        setContentView(root);
    }

    private void loadPreset() {
        pendingScale = prefs.getFloat(KEY_SCALE, 1f);
        int type = prefs.getInt(KEY_TYPE, 0);
        pendingScale = Math.max(0.25f, Math.min(5f, pendingScale));
        seekBar.setProgress(Math.round(pendingScale * 100f - 25f));
        shapes.setSelection(Math.max(0, Math.min(3, type)));
        updatePreview();
    }

    private void updatePreview() {
        scaleText.setText(String.format(Locale.US, "%.0f%%", pendingScale * 100f));
        preview.setScale(pendingScale);
        updateStatus();
    }

    private void updateStatus() {
        status.setText("Preset lokal: " + String.format(Locale.US, "%.0f%%", pendingScale * 100f)
                + " • Bentuk: " + (shapes == null ? "Arrow" : shapes.getSelectedItem()));
    }
}