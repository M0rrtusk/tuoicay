package com.example.tuoicayiot;

import android.content.Intent;
import android.content.SharedPreferences; // Cần thiết cho SharedPreferences (giả định dùng để theo dõi trạng thái đăng nhập)
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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

public class UserActivity extends AppCompatActivity {

    private static final String TAG = "UserActivity_Firebase";
    private static final String DATABASE_URL = "https://smartwatering-7f76b-default-rtdb.asia-southeast1.firebasedatabase.app/";

    // Khai báo hằng số SharedPreferences
    private static final String APP_PREFS = "APP";
    private static final String KEY_LOGGED_OUT = "LOGGED_OUT";

    TextView tvMoisture, tvStatus, tvHumidity, tvTemperature, tvMode, tvThreshold;
    // THÊM btnLogout
    Button btnOn, btnOff, btnAuto, btnManual, btnSetThreshold, btnLogout;
    EditText etThreshold;

    private String deviceId;
    private String userRole = "";
    private DatabaseReference deviceRef;
    private FirebaseDatabase firebaseDatabase;
    private FirebaseAuth mAuth; // KHAI BÁO FIREBASE AUTH

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // KHỞI TẠO FIREBASE AUTH
        mAuth = FirebaseAuth.getInstance();

        deviceId = getIntent().getStringExtra("DEVICE_ID");
        String roleFromIntent = getIntent().getStringExtra("ROLE");

        if (deviceId == null) {
            Toast.makeText(this, "Lỗi: Không có ID thiết bị.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, DeviceSetupActivity.class));
            finish();
            return;
        }

        userRole = (roleFromIntent != null) ? roleFromIntent.toLowerCase() : "customer";

        firebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL);
        deviceRef = firebaseDatabase.getReference("devices").child(deviceId);

        // --- ÁNH XẠ VIEW ---
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

        // ÁNH XẠ NÚT ĐẶT NGƯỠNG VÀ ĐĂNG XUẤT
        etThreshold = findViewById(R.id.etThreshold);
        btnSetThreshold = findViewById(R.id.btnSetThreshold);
        btnLogout = findViewById(R.id.btnLogout); // ÁNH XẠ NÚT ĐĂNG XUẤT (Giả định ID là btnLogout)

        // VÔ HIỆU HÓA/KÍCH HOẠT NÚT DỰA TRÊN ROLE
        updateButtonStatesBasedOnRole(userRole);


        // Lắng nghe dữ liệu thiết bị
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

        // ============================
        //           SỰ KIỆN NÚT
        // ============================

        btnAuto.setOnClickListener(v -> checkPermissionThenRun("user", () -> deviceRef.child("controlMode").setValue(1)));
        btnManual.setOnClickListener(v -> checkPermissionThenRun("user", () -> deviceRef.child("controlMode").setValue(0)));
        btnOn.setOnClickListener(v -> checkPermissionThenRun("user", () -> deviceRef.child("pump").setValue(2)));
        btnOff.setOnClickListener(v -> checkPermissionThenRun("user", () -> deviceRef.child("pump").setValue(1)));
        btnSetThreshold.setOnClickListener(v -> checkPermissionThenRun("manager", this::setThreshold));

        // XỬ LÝ SỰ KIỆN ĐĂNG XUẤT
        btnLogout.setOnClickListener(v -> logoutUser());
    }

    // ============================
    //      HÀM ĐĂNG XUẤT MỚI
    // ============================
    private void logoutUser() {
        // 1. (Tùy chọn) Đặt cờ LOGGED_OUT nếu bạn dùng SharedPreferences
        SharedPreferences prefs = getSharedPreferences(APP_PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_LOGGED_OUT, true).apply();

        // 2. Đăng xuất khỏi Firebase
        mAuth.signOut();

        // 3. Chuyển về màn hình Login
        Intent intent = new Intent(UserActivity.this, LoginActivity.class);
        // Xóa tất cả các Activity khỏi stack
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();

        Toast.makeText(this, "Bạn đã đăng xuất thành công.", Toast.LENGTH_SHORT).show();
    }


    // --- HÀM VÔ HIỆU HÓA/KÍCH HOẠT NÚT ---
    private void setControlButtonsEnabled(boolean enabled) {
        btnOn.setEnabled(enabled);
        btnOff.setEnabled(enabled);
        btnAuto.setEnabled(enabled);
        btnManual.setEnabled(enabled);
        btnSetThreshold.setEnabled(enabled);
        etThreshold.setEnabled(enabled);
    }

    // --- HÀM ÁP DỤNG TRẠNG THÁI NÚT DỰA TRÊN ROLE ĐÃ TẢI XUỐNG ---
    private void updateButtonStatesBasedOnRole(String role) {
        // Vô hiệu hóa tất cả trước
        setControlButtonsEnabled(false);

        // 1. User (Điều khiển cơ bản)
        if (role.equals("user")) {
            btnAuto.setEnabled(true);
            btnManual.setEnabled(true);
            btnOn.setEnabled(true);
            btnOff.setEnabled(true);
            etThreshold.setEnabled(false); // User không đặt được ngưỡng
        }
        // 2. Manager (Điều khiển + Ngưỡng)
        else if (role.equals("manager")) {
            setControlButtonsEnabled(true);
        }
        // 3. Customer (Không có quyền điều khiển, mặc định là disabled)
    }


    // ============================
    //      HÀM KIỂM TRA QUYỀN (Logic cốt lõi)
    // ============================
    private void checkPermissionThenRun(String allowedRole, Runnable action) {
        String currentUserRole = userRole;

        boolean hasPermission = false;

        if (currentUserRole.equals("manager")) {
            hasPermission = true;
        } else if (currentUserRole.equals("user")) {
            // User chỉ có quyền nếu allowedRole là 'user'
            if (allowedRole.equals("user")) {
                hasPermission = true;
            }
        }

        if (hasPermission) {
            action.run();
        } else {
            Toast.makeText(this, "Bạn (" + userRole + ") không có quyền thực hiện yêu cầu này (Chỉ Manager mới được Đặt Ngưỡng).", Toast.LENGTH_LONG).show();
        }
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
        String status = snapshot.child("status").getValue(String.class);
        Integer controlMode = snapshot.child("controlMode").getValue(Integer.class);
        Integer threshold = snapshot.child("threshold").getValue(Integer.class);

        if (moisture != null) tvMoisture.setText("Độ ẩm đất: " + moisture + " %");
        if (temperature != null) tvTemperature.setText(String.format(Locale.getDefault(), "Nhiệt độ: %.1f °C", temperature));
        if (humidity != null) tvHumidity.setText(String.format(Locale.getDefault(), "Độ ẩm không khí: %.1f %%", humidity));

        if (status != null)
            tvStatus.setText("Trạng thái bơm: " + status.replace("_", " "));

        if (controlMode != null) {
            String modeStr = controlMode == 1 ? "TỰ ĐỘNG" : "THỦ CÔNG";
            tvMode.setText("Chế độ: " + modeStr);

            // Logic bật/tắt ON/OFF dựa trên chế độ (Chỉ khi là User/Manager)
            if (userRole.equals("user") || userRole.equals("manager")) {
                boolean isManual = controlMode == 0;

                // Tránh ghi đè trạng thái disabled của updateButtonStatesBasedOnRole nếu không ở chế độ Manual
                if (isManual) {
                    btnOn.setEnabled(true);
                    btnOff.setEnabled(true);
                } else {
                    // Nếu ở chế độ TỰ ĐỘNG, thì các nút ON/OFF phải disabled
                    btnOn.setEnabled(false);
                    btnOff.setEnabled(false);
                }
            }
        }

        if (threshold != null) tvThreshold.setText("Ngưỡng tưới: " + threshold + " %");
    }
}