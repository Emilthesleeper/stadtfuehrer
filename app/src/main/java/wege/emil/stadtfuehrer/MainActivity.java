package wege.emil.stadtfuehrer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    final static String defaultText = "\"Die App \\\"Stadtführer Görlitz\\\", gibt basierend auf dem Standort beim Laufen durch Görlitz zufällig Informationen über Gebäude und Sehenswürdigkeiten im Umkreis von 50m. Die App entsteht im Rahmen einer Arbeit um zu zeigen, wie gefährlich Standortortung sein kann, und wie man sie gut einsetzen kann.\"";
    final static String[] PERMISSIONS = {android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION};
    final static int PERMISSION_ALL = 1;

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switch1;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switch2;

    private Typeface Rubik;
    private Typeface Cascadia;
    private TextView textView;
    private Intent mainServiceIntent;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(PERMISSIONS, PERMISSION_ALL);
        }

        switch1 = findViewById(R.id.switch1);
        switch2 = findViewById(R.id.switch2);
        textView = findViewById(R.id.textView1);
        Rubik = ResourcesCompat.getFont(this, R.font.rubik);
        Cascadia = ResourcesCompat.getFont(this, R.font.cascadia);
        MainService mainService;
        try {
            mainService = MainService.class.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            throw new RuntimeException(e);
        }
        mainServiceIntent = new Intent(this, mainService.getClass());

        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String message = intent.getStringExtra(MainService.MESSAGE);
                        textView.setText(message + "\n" + textView.getText());
                    }
                }, new IntentFilter(MainService.ACTION_LOCATION_BROADCAST)
        );

        switch1.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                textView.setText("Die App läuft im Hintergrund.");
                startService(mainServiceIntent);
            } else {
                textView.setText(defaultText);
                switch2.setChecked(false);
                stopService(mainServiceIntent);
            }
        });
        switch2.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!switch1.isChecked()) {
                    switch2.setChecked(false);
                } else {
                    MainService.debugMode = true;
                    textView.setTextSize(15);
                    textView.setTypeface((android.graphics.Typeface) Cascadia);
                }
            } else {
                MainService.debugMode = false;
                if (!switch1.isChecked()) {
                    textView.setText(defaultText);
                } else {
                    textView.setText("Die App läuft im Hintergrund.");
                }
                textView.setTextSize(24);
                textView.setTypeface((android.graphics.Typeface) Rubik);
            }
        });
    }

    @Override
    protected void onDestroy() {
        stopService(mainServiceIntent);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            finish();
        }
    }
}