package com.example.tuoicayiot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences; // Thư viện cần thiết
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class DeviceSetupActivity extends AppCompatActivity {

    private static final String TAG = "DeviceSetupActivity";
    private static final String DATABASE_URL = "https://smartwatering-7f76b-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    // --- SHARED PREFERENCES CONSTANTS ---
    private static final String PREF_NAME = "SmartWateringPrefs";
    private static final String KEY_DEVICE_ID = "configured_device_id";
    // --- END SHARED PREFERENCES CONSTANTS ---

    // UI elements
    private Spinner spEspNetwork;
    private Button btnScanWifi;
    private EditText etHomeWifiSsid, etHomeWifiPass, etDeviceName;
    private Button btnProvision;
    private FirebaseAuth mAuth;
    private FirebaseDatabase firebaseDatabase;

    // Provisioning logic
    private List<String> espNetworksList;
    private String selectedChipId = null;
    // SỬ DỤNG CHIP ID THẬT CỦA BẠN (Đã xác nhận từ log Firebase)
    private final String MOCK_CHIP_ID = "25077000";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_setup);

        mAuth = FirebaseAuth.getInstance();

        // KHỞI TẠO FIREBASE DATABASE (Vẫn cần dùng cho phần Ràng buộc quyền sở hữu)
        try {
            firebaseDatabase = FirebaseDatabase.getInstance(DATABASE_URL);
        } catch (Exception e) {
            Log.e(TAG, "Firebase init error: " + e.getMessage());
            Toast.makeText(this, "Lỗi khởi tạo DB: Kiểm tra URL", Toast.LENGTH_LONG).show();
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Ánh xạ View
        spEspNetwork = findViewById(R.id.sp_esp_network);
        btnScanWifi = findViewById(R.id.btn_scan_wifi);
        etHomeWifiSsid = findViewById(R.id.et_home_wifi_ssid);
        etHomeWifiPass = findViewById(R.id.et_home_wifi_pass);
        etDeviceName = findViewById(R.id.et_device_name);
        btnProvision = findViewById(R.id.btn_provision);

        // Khởi tạo danh sách mạng giả lập
        espNetworksList = new ArrayList<>();
        espNetworksList.add("SW-" + MOCK_CHIP_ID + " (Đã chọn)"); // Giả lập đã quét và chọn
        updateEspNetworkSpinner();
        selectedChipId = MOCK_CHIP_ID; // Thiết lập Chip ID để có thể Ràng buộc

        // Xử lý sự kiện Quét: Mở Settings WiFi
        btnScanWifi.setOnClickListener(v -> openWifiSettings());

        // Xử lý sự kiện Ràng buộc
        btnProvision.setOnClickListener(v -> setupDeviceConfiguration());

        checkLocationPermissionAndGetSsid();
    }

    // --- CÁC HÀM HỖ TRỢ WIFI VÀ QUYỀN (GIỮ NGUYÊN) ---
    private void checkLocationPermissionAndGetSsid() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            setInitialHomeWifiSsid();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setInitialHomeWifiSsid();
            } else {
                Toast.makeText(this, "Không thể tự động điền SSID.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setInitialHomeWifiSsid() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return;

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                String ssid = wifiInfo.getSSID();
                if (ssid != null && !ssid.contains("unknown")) {
                    ssid = ssid.replace("\"", "");
                    etHomeWifiSsid.setText(ssid);
                }
            }
        }
    }

    private void updateEspNetworkSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                espNetworksList
        );
        spEspNetwork.setAdapter(adapter);
    }

    private void openWifiSettings() {
        Toast.makeText(this, "Vui lòng tìm mạng SW-XXXX và kết nối.", Toast.LENGTH_LONG).show();

        try {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // --- HÀM RÀNG BUỘC VÀ LƯU CẤU HÌNH FIREBASE ---
    private void setupDeviceConfiguration() {
        String homeWifiSsid = etHomeWifiSsid.getText().toString().trim();
        String homeWifiPass = etHomeWifiPass.getText().toString().trim();
        String deviceName = etDeviceName.getText().toString().trim();
        String uid = mAuth.getCurrentUser().getUid();

        if (selectedChipId == null || selectedChipId.isEmpty() || homeWifiSsid.isEmpty() || homeWifiPass.isEmpty() || deviceName.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đủ thông tin cấu hình.", Toast.LENGTH_LONG).show();
            return;
        }

        // BƯỚC 1: GIẢ LẬP GỬI THÔNG TIN WIFI, UID, và TÊN ĐẾN ESP32

        // HƯỚNG DẪN NGƯỜI DÙNG NHẬP UID VÀO PORTAL 192.168.4.1 (BẮT BUỘC)
        Toast.makeText(this,
                "QUAN TRỌNG: Hãy nhập UID sau vào cổng 192.168.4.1 để ESP32 hoạt động: " + uid,
                Toast.LENGTH_LONG).show();

        Log.d(TAG, "Gửi cấu hình (Giả lập): SSID=" + homeWifiSsid + ", UID=" + uid);

        // BƯỚC 2: RÀNG BUỘC THIẾT BỊ VÀ KHỞI TẠO CẤU HÌNH TRÊN FIREBASE

        // 2a. Ràng buộc quyền sở hữu (users/<UID>/devices/<ChipID>)
        DatabaseReference userDeviceRef = firebaseDatabase
                .getReference("users")
                .child(uid)
                .child("devices");

        userDeviceRef.child(selectedChipId).child("name").setValue(deviceName)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {

                        DatabaseReference deviceConfigRef = firebaseDatabase
                                .getReference("devices")
                                .child(selectedChipId);

                        deviceConfigRef.child("ownerUID").setValue(uid);
                        deviceConfigRef.child("ownerEmail").setValue(mAuth.getCurrentUser().getEmail()); // Thêm Email
                        deviceConfigRef.child("deviceName").setValue(deviceName);

                        deviceConfigRef.child("controlMode").setValue(0);
                        deviceConfigRef.child("threshold").setValue(40);
                        deviceConfigRef.child("pump").setValue(1);

                        SharedPreferences sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putString(KEY_DEVICE_ID, selectedChipId);
                        editor.apply();

                        Toast.makeText(DeviceSetupActivity.this, "Cấu hình thành công. Đang chuyển trang...", Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(DeviceSetupActivity.this, MainActivity.class);
                        intent.putExtra("DEVICE_ID", selectedChipId);
                        intent.putExtra("USER_EMAIL", mAuth.getCurrentUser().getEmail());
                        startActivity(intent);
                        finish();
                    } else {
                        Log.e(TAG, "Firebase Save Error: ", task.getException());
                        Toast.makeText(DeviceSetupActivity.this, "Lỗi khi lưu cấu hình Firebase.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}