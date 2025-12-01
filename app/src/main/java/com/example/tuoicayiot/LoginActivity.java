package com.example.tuoicayiot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DatabaseError;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegisterLink;
    private FirebaseAuth mAuth;

    private static final String PREF_NAME = "SmartWateringPrefs";
    private static final String KEY_DEVICE_ID = "configured_device_id";
    private static final String DATABASE_URL = "https://smartwatering-7f76b-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.et_email_login);
        etPassword = findViewById(R.id.et_password_login);
        btnLogin = findViewById(R.id.btn_login);
        tvRegisterLink = findViewById(R.id.tv_register_link);

        // Kiểm tra đăng nhập (Giữ nguyên)
        if (mAuth.getCurrentUser() != null) {
            fetchUserRoleAndProceed();
            return;
        }

        // Xử lý chuyển sang màn hình Đăng ký
        if (tvRegisterLink != null) {
            tvRegisterLink.setOnClickListener(v -> {
                Intent registerIntent = new Intent(this, RegisterActivity.class);
                startActivity(registerIntent);
            });
        }

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Điền đủ Email và Mật khẩu!", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(LoginActivity.this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show();
                            fetchUserRoleAndProceed();
                        } else {
                            String errorMessage;
                            Exception exception = task.getException();

                            if (exception instanceof FirebaseAuthInvalidUserException) {
                                errorMessage = "Tài khoản Email này không tồn tại.";
                            } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                                errorMessage = "Sai mật khẩu. Vui lòng thử lại.";
                            } else {
                                errorMessage = "Lỗi đăng nhập: " + exception.getMessage();
                            }

                            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void fetchUserRoleAndProceed() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToNextActivity("guest");
            return;
        }
        String uid = user.getUid();

        DatabaseReference userRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("users")
                .child(uid)
                .child("role");

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String role = snapshot.getValue(String.class);
                if (role == null || role.isEmpty()) role = "customer";

                goToNextActivity(role);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(LoginActivity.this, "Lỗi tải role DB: " + error.getMessage(), Toast.LENGTH_LONG).show();
                goToNextActivity("customer"); // Fallback
            }
        });
    }

    private void goToNextActivity(String role) {
        SharedPreferences sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String deviceId = sharedPref.getString(KEY_DEVICE_ID, null);
        String userEmail = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : null;

        Intent intent;
        if (deviceId != null && !deviceId.isEmpty()) {
            String lowerCaseRole = role.toLowerCase();

            // CHUYỂN ĐẾN USERACTIVITY HOẶC CUSTOMERACTIVITY DỰA TRÊN ROLE
            if (lowerCaseRole.equals("user")) {
                intent = new Intent(LoginActivity.this, CustomerActivity.class);
            } else {
                // USER hoặc MANAGER đi đến Activity điều khiển
                intent = new Intent(LoginActivity.this, UserActivity.class); // <--- ĐÃ SỬA
            }

            intent.putExtra("DEVICE_ID", deviceId);
            intent.putExtra("USER_EMAIL", userEmail);
        } else {
            // Chưa có thiết bị -> Chuyển đến màn hình thiết lập
            intent = new Intent(LoginActivity.this, DeviceSetupActivity.class);
        }

        intent.putExtra("ROLE", role);
        startActivity(intent);
        finish();
    }
}