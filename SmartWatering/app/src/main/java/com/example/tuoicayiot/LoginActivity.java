package com.example.tuoicayiot; // Đã sửa package name chính xác

import android.content.Intent;
import android.content.SharedPreferences; // Thư viện cần thiết
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context; // Cần thiết cho SharedPreferences

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser; // Cần thiết cho getCurrentUser

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegisterLink;
    private FirebaseAuth mAuth;

    // Khai báo SharedPreferences key (Phải khớp với DeviceSetupActivity)
    private static final String PREF_NAME = "SmartWateringPrefs";
    private static final String KEY_DEVICE_ID = "configured_device_id";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Khởi tạo Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // 1. Ánh xạ View
        etEmail = findViewById(R.id.et_email_login);
        etPassword = findViewById(R.id.et_password_login);
        btnLogin = findViewById(R.id.btn_login);
        tvRegisterLink = findViewById(R.id.tv_register_link);

        // BƯỚC 1: KIỂM TRA TRẠNG THÁI NGƯỜI DÙNG VÀ PERSISTENCE
        // Nếu đã đăng nhập, kiểm tra xem có thiết bị đã cấu hình chưa
        if (mAuth.getCurrentUser() != null) {
            checkAndGoToNextActivity();
            return;
        }

        // Xử lý chuyển sang màn hình Đăng ký
        tvRegisterLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        // Xử lý Đăng nhập bằng Firebase Authentication
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Điền đủ Email và Mật khẩu!", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        public void onComplete(@NonNull Task<AuthResult> task) { // Đã sửa lỗi void
                            if (task.isSuccessful()) {
                                Toast.makeText(LoginActivity.this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show();
                                checkAndGoToNextActivity(); // GỌI HÀM KIỂM TRA SAU ĐĂNG NHẬP THÀNH CÔNG
                            } else {
                                Toast.makeText(LoginActivity.this, "Đăng nhập thất bại: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        });
    }

    private void checkAndGoToNextActivity() {
        SharedPreferences sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String deviceId = sharedPref.getString(KEY_DEVICE_ID, null); // Đọc ID đã lưu

        String userEmail = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : null;

        if (deviceId != null) {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("DEVICE_ID", deviceId); // Truyền ID đã lưu
            intent.putExtra("USER_EMAIL", userEmail); // Truyền Email
            startActivity(intent);
        } else {
            Intent intent = new Intent(LoginActivity.this, DeviceSetupActivity.class);
            startActivity(intent);
        }
        finish();
    }
}