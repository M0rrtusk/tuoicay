package com.example.tuoicayiot;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity_Firebase";
    private static final String DATABASE_URL = "https://smartwatering-7f76b-default-rtdb.asia-southeast1.firebasedatabase.app/";

    TextView tvMoisture, tvStatus, tvHumidity, tvTemperature, tvMode, tvThreshold; // Khai báo
    Button btnOn, btnOff, btnAuto, btnManual, btnSetThreshold;
    EditText etThreshold;

    private String deviceId;
    private DatabaseReference deviceRef;
    private FirebaseDatabase firebaseDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceId = getIntent().getStringExtra("DEVICE_ID");
        if (deviceId == null) {
            Toast.makeText(this, "Không có ID thiết bị. Khởi tạo lại luồng Setup.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, DeviceSetupActivity.class));
            finish();
            return;
        }

        try {
            firebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL);
            deviceRef = firebaseDatabase.getReference("devices").child(deviceId);
        } catch (Exception e) {
            Log.e(TAG, "Firebase init error: " + e.getMessage());
            Toast.makeText(this, "Lỗi kết nối DB.", Toast.LENGTH_LONG).show();
            return;
        }

        tvMoisture = findViewById(R.id.tvMoisture);
        tvStatus = findViewById(R.id.tvStatus);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvMode = findViewById(R.id.tvMode);
        tvThreshold = findViewById(R.id.tvThreshold);
        btnOn = findViewById(R.id.btnOn);
        btnOff = findViewById(R.id.btnOff);
        btnAuto = findViewById(R.id.btnAuto);
        btnManual = findViewById(R.id.btnManual);

        etThreshold = findViewById(R.id.etThreshold);
        btnSetThreshold = findViewById(R.id.btnSetThreshold);

        deviceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                updateUI(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase listener cancelled: " + error.getMessage());
            }
        });

        btnAuto.setOnClickListener(v -> deviceRef.child("controlMode").setValue(1));
        btnManual.setOnClickListener(v -> deviceRef.child("controlMode").setValue(0));

        btnOn.setOnClickListener(v -> deviceRef.child("pump").setValue(2));
        btnOff.setOnClickListener(v -> deviceRef.child("pump").setValue(1));

        btnSetThreshold.setOnClickListener(v -> setThreshold());
    }

    private void setThreshold() {
        String input = etThreshold.getText().toString();
        try {
            int threshold = Integer.parseInt(input);
            if (threshold >= 5 && threshold <= 95) {
                deviceRef.child("threshold").setValue(threshold);
                Toast.makeText(this, "Đã gửi ngưỡng: " + threshold + "%", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Ngưỡng phải từ 5% đến 95%.", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Vui lòng nhập số hợp lệ.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI(DataSnapshot snapshot) {

        Integer moisture = snapshot.child("moisture").getValue(Integer.class);
        Double temperature = snapshot.child("temperature").getValue(Double.class);
        Double humidity = snapshot.child("humidity").getValue(Double.class);
        String status = snapshot.child("status").getValue(String.class); // Trạng thái bơm thực tế (AUTO_ON, MANUAL_OFF,...)
        Integer controlMode = snapshot.child("controlMode").getValue(Integer.class);
        Integer threshold = snapshot.child("threshold").getValue(Integer.class);

        if (moisture != null) {
            tvMoisture.setText("Độ ẩm đất: " + moisture + " %");
        }

        if (temperature != null) {
            tvTemperature.setText(String.format(Locale.getDefault(), "Nhiệt độ: %.1f °C", temperature));
        }
        if (humidity != null) {
            tvHumidity.setText(String.format(Locale.getDefault(), "Độ ẩm không khí: %.1f %%", humidity));
        }

        if (status != null) {
            tvStatus.setText("Trạng thái bơm: " + status.replace("_", " "));
        }

        if (controlMode != null) {
            String modeStr = controlMode == 1 ? "TỰ ĐỘNG" : "THỦ CÔNG";
            tvMode.setText("Chế độ: " + modeStr);

            boolean isManual = controlMode == 0;
            btnOn.setEnabled(isManual);
            btnOff.setEnabled(isManual);
        }

        if (threshold != null) {
            tvThreshold.setText("Ngưỡng tưới: " + threshold + " %");
        }
    }
}