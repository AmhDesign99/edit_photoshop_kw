package com.amhdesign.pointercustomizer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.content.pm.PackageManager;
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

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int SHIZUKU_PERMISSION_CODE = 2417;
    private static final String PREFS = "pointer_prefs";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_TYPE = "type";

    private TextView status;
    private TextView shizukuStatus;
    private TextView scaleText;
    private PointerPreviewView preview;
    private SeekBar seekBar;
    private Spinner shapes;
    private android.content.SharedPreferences prefs;
    private float pendingScale = 1.0f;
    private IPointerUserService remoteService;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_PERMISSION_CODE) {
                    updateShizukuStatus();
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        bindRemoteService();
                    } else {
                        Toast.makeText(this, "Izin Shizuku ditolak.", Toast.LENGTH_LONG).show();
                    }
                }
            };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, android.os.IBinder service) {
            remoteService = IPointerUserService.Stub.asInterface(service);
            updateShizukuStatus();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            remoteService = null;
            updateShizukuStatus();
        }
    };

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
        Shizuku.addRequestPermissionResultListener(permissionListener);
        buildUi();
        loadPreset();
        updateShizukuStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        updateShizukuStatus();
    }

    @Override protected void onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener);
        } catch (Throwable ignored) {}
        try {
            if (remoteService != null) {
                Shizuku.unbindUserService(userServiceArgs(), serviceConnection, false);
            }
        } catch (Throwable ignored) {}
        super.onDestroy();
    }

    private Shizuku.UserServiceArgs userServiceArgs() {
        return new Shizuku.UserServiceArgs(
                new ComponentName(this, PointerUserService.class))
                .daemon(false)
                .debuggable(BuildConfig.DEBUG)
                .version(1)
                .tag("pointer-customizer-service");
    }

    private boolean shizukuGranted() {
        try {
            return Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    private void requestOrBindShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this,
                        "Shizuku belum berjalan. Install dan jalankan Shizuku melalui Wireless debugging.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (!shizukuGranted()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE);
                return;
            }
            bindRemoteService();
        } catch (Throwable e) {
            Toast.makeText(this, "Shizuku error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void bindRemoteService() {
        try {
            Shizuku.bindUserService(userServiceArgs(), serviceConnection);
            updateShizukuStatus();
        } catch (Throwable e) {
            Toast.makeText(this, "Gagal menghubungkan UserService: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void updateShizukuStatus() {
        if (shizukuStatus == null) return;
        try {
            if (!Shizuku.pingBinder()) {
                shizukuStatus.setText("Shizuku: belum berjalan");
            } else if (!shizukuGranted()) {
                shizukuStatus.setText("Shizuku: berjalan, izin aplikasi belum diberikan");
            } else if (remoteService == null) {
                shizukuStatus.setText("Shizuku: izin aktif, UserService belum terhubung");
            } else {
                int uid;
                try { uid = remoteService.getUid(); } catch (Throwable t) { uid = -1; }
                shizukuStatus.setText("Shizuku: TERHUBUNG • UID " + uid);
            }
        } catch (Throwable e) {
            shizukuStatus.setText("Shizuku: status tidak tersedia");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackgroundColor(Color.rgb(17, 19, 24));

        TextView title = label("Pointer Customizer PRO", 26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = label("System-wide cursor melalui Shizuku • Android 14+", 14);
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        shizukuStatus = label("", 14);
        shizukuStatus.setTextColor(Color.rgb(120, 220, 160));
        root.addView(shizukuStatus);

        Button connect = new Button(this);
        connect.setText("Hubungkan Shizuku");
        root.addView(connect);
        connect.setOnClickListener(v -> requestOrBindShizuku());

        preview = new PointerPreviewView(this);
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(170)));

        root.addView(label("Ukuran pointer", 17));
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

        root.addView(label("Bentuk cursor", 17));
        shapes = new Spinner(this);
        String[] names = {"Arrow", "Circle", "Crosshair", "Dot"};
        shapes.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        root.addView(shapes);
        shapes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                preview.setPointerType(pos);
                prefs.edit().putInt(KEY_TYPE, pos).apply();
                updateStatus();
            }
        });

        Button apply = new Button(this);
        apply.setText("TERAPKAN KE SYSTEM");
        root.addView(apply);
        apply.setOnClickListener(v -> applySystemCursor());

        Button reset = new Button(this);
        reset.setText("Kembalikan cursor bawaan");
        root.addView(reset);
        reset.setOnClickListener(v -> resetSystemCursor());

        Button save = new Button(this);
        save.setText("Simpan preset");
        root.addView(save);
        save.setOnClickListener(v -> {
            prefs.edit().putFloat(KEY_SCALE, pendingScale).apply();
            Toast.makeText(this, "Preset tersimpan", Toast.LENGTH_SHORT).show();
            updateStatus();
        });

        status = label("", 13);
        status.setTextColor(Color.LTGRAY);
        root.addView(status);

        TextView note = label(
                "Shizuku diperlukan karena perubahan cursor global memanggil InputManager pada level sistem. " +
                "Tanpa Shizuku aplikasi hanya dapat mengubah cursor pada window miliknya sendiri.",
                12);
        note.setTextColor(Color.GRAY);
        root.addView(note);

        setContentView(root);
    }

    private void applySystemCursor() {
        if (remoteService == null) {
            Toast.makeText(this, "Hubungkan Shizuku terlebih dahulu.", Toast.LENGTH_LONG).show();
            requestOrBindShizuku();
            return;
        }
        try {
            Bitmap bitmap = buildCursorBitmap();
            boolean ok = remoteService.setCustomPointer(bitmap, hotspotX(), hotspotY());
            if (ok) {
                Toast.makeText(this, "Cursor system-wide diterapkan.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Android/HyperOS menolak perubahan cursor.", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable e) {
            Toast.makeText(this, "Gagal menerapkan: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resetSystemCursor() {
        if (remoteService == null) {
            Toast.makeText(this, "Hubungkan Shizuku terlebih dahulu.", Toast.LENGTH_LONG).show();
            requestOrBindShizuku();
            return;
        }
        try {
            boolean ok = remoteService.resetPointer();
            Toast.makeText(this, ok ? "Cursor bawaan dikembalikan." : "Reset ditolak sistem.",
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(this, "Gagal reset: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int hotspotX() {
        return shapes.getSelectedItemPosition() == 0 ? 2 : Math.max(1, cursorPixels() / 2);
    }

    private int hotspotY() {
        return shapes.getSelectedItemPosition() == 0 ? 2 : Math.max(1, cursorPixels() / 2);
    }

    private int cursorPixels() {
        return Math.max(24, Math.min(256, Math.round(32f * pendingScale)));
    }

    private Bitmap buildCursorBitmap() {
        int size = cursorPixels();
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE);
        stroke.setColor(Color.BLACK);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(2f, size / 16f));

        int type = shapes.getSelectedItemPosition();
        float mid = size / 2f;
        if (type == 0) {
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(2, 2);
            p.lineTo(size * .36f, size * .72f);
            p.lineTo(size * .48f, size * .56f);
            p.lineTo(size * .78f, size * .88f);
            p.lineTo(size * .92f, size * .74f);
            p.lineTo(size * .62f, size * .43f);
            p.lineTo(size * .80f, size * .36f);
            p.close();
            c.drawPath(p, fill);
            c.drawPath(p, stroke);
        } else if (type == 1) {
            c.drawCircle(mid, mid, size * .34f, fill);
            c.drawCircle(mid, mid, size * .34f, stroke);
        } else if (type == 2) {
            c.drawLine(2, mid, size - 2, mid, stroke);
            c.drawLine(mid, 2, mid, size - 2, stroke);
            c.drawCircle(mid, mid, size * .12f, fill);
        } else {
            c.drawCircle(mid, mid, size * .17f, fill);
            c.drawCircle(mid, mid, size * .17f, stroke);
        }
        return b;
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
        status.setText("Ukuran: " + String.format(Locale.US, "%.0f%%", pendingScale * 100f)
                + " • Bentuk: " + (shapes == null ? "Arrow" : shapes.getSelectedItem()));
    }
}