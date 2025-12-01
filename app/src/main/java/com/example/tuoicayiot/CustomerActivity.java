package com.example.tuoicayiot;

import android.content.Intent;
import android.content.SharedPreferences; // Cần thiết nếu bạn dùng SharedPreferences để theo dõi trạng thái
import android.os.Bundle;
import android.util.Log;
import android.widget.Button; // Thêm import cho Button
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth; // Cần thiết cho Đăng xuất
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class CustomerActivity extends AppCompatActivity {

    private static final String TAG = "CustomerActivity";
    private static final String DATABASE_URL = "https://smartwatering-7f76b-default-rtdb.asia-southeast1.firebasedatabase.app/";
    // Khai báo các hằng số SharedPreferences (Nếu bạn dùng nó để kiểm tra login)
    private static final String APP_PREFS = "APP";
    private static final String KEY_LOGGED_OUT = "LOGGED_OUT";

    TextView tvMoisture, tvStatus, tvHumidity, tvTemperature, tvMode, tvThreshold;
    Button btnLogout; // THÊM NÚT ĐĂNG XUẤT

    private String deviceId;
    private DatabaseReference deviceRef;
    private FirebaseDatabase firebaseDatabase;
    private FirebaseAuth mAuth; // KHAI BÁO FIREBASE AUTH

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);

        // KHỞI TẠO FIREBASE AUTH
        mAuth = FirebaseAuth.getInstance();

        deviceId = getIntent().getStringExtra("DEVICE_ID");
        if (deviceId == null) {
            Toast.makeText(this, "Lỗi: Không tìm thấy ID thiết bị.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        firebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL);
        deviceRef = firebaseDatabase.getReference("devices").child(deviceId);

        // --- ÁNH XẠ VIEW CHỈ CẦN THIẾT ---
        tvMoisture = findViewById(R.id.tvMoisture);
        tvStatus = findViewById(R.id.tvStatus);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvMode = findViewById(R.id.tvMode);
        tvThreshold = findViewById(R.id.tvThreshold);

        // ÁNH XẠ NÚT ĐĂNG XUẤT (Giả định ID là btnLogout)
        btnLogout = findViewById(R.id.btnLogout);

        // Customer chỉ lắng nghe dữ liệu
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

        // XỬ LÝ SỰ KIỆN ĐĂNG XUẤT
        btnLogout.setOnClickListener(v -> logoutUser());
    }

    // ==========================
    // HÀM ĐĂNG XUẤT MỚI
    // ==========================
    private void logoutUser() {
        // 1. (Tùy chọn) Đặt cờ LOGGED_OUT nếu bạn dùng SharedPreferences
        SharedPreferences prefs = getSharedPreferences(APP_PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_LOGGED_OUT, true).apply();

        // 2. Đăng xuất khỏi Firebase
        mAuth.signOut();

        // 3. Chuyển về màn hình Login
        Intent intent = new Intent(CustomerActivity.this, LoginActivity.class);
        // Xóa tất cả các Activity khỏi stack để người dùng không quay lại được
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();

        Toast.makeText(this, "Bạn đã đăng xuất thành công.", Toast.LENGTH_SHORT).show();
    }

    private void updateUI(DataSnapshot snapshot) {
        Integer moisture = snapshot.child("moisture").getValue(Integer.class);
        Double temperature = snapshot.child("temperature").getValue(Double.class);
        Double humidity = snapshot.child("humidity").getValue(Double.class);
        String status = snapshot.child("status").getValue(String.class);
        Integer controlMode = snapshot.child("controlMode").getValue(Integer.class);
        Integer threshold = snapshot.child("threshold").getValue(Integer.class);

        // Cập nhật UI (Chỉ xem)
        if (moisture != null) tvMoisture.setText("Độ ẩm đất: " + moisture + " %");
        if (temperature != null) tvTemperature.setText(String.format(Locale.getDefault(), "Nhiệt độ: %.1f °C", temperature));
        if (humidity != null) tvHumidity.setText(String.format(Locale.getDefault(), "Độ ẩm không khí: %.1f %%", humidity));

        if (status != null)
            tvStatus.setText("Trạng thái bơm: " + status.replace("_", " "));

        if (controlMode != null) {
            String modeStr = controlMode == 1 ? "TỰ ĐỘNG" : "THỦ CÔNG";
            tvMode.setText("Chế độ: " + modeStr);
        }

        if (threshold != null) tvThreshold.setText("Ngưỡng tưới: " + threshold + " %");
    }
}