package com.example.tuoicayiot;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnRegister;
    private FirebaseAuth mAuth;
    private DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance().getReference("users");

        etEmail = findViewById(R.id.et_email_register);
        etPassword = findViewById(R.id.et_password_register);
        btnRegister = findViewById(R.id.btn_register);

        btnRegister.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty() || password.length() < 6) {
                Toast.makeText(this, "Vui lòng điền Email và Mật khẩu (tối thiểu 6 ký tự)!", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. GỌI DỊCH VỤ FIREBASE AUTH ĐỂ TẠO TÀI KHOẢN
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {

                                // LẤY UID ĐƯỢC TẠO RA BỞI FIREBASE AUTH
                                String uid = mAuth.getCurrentUser().getUid();

                                // 2. LƯU THÔNG TIN BỔ SUNG (role, email) VÀO REALTIME DB DƯỚI UID
                                HashMap<String, Object> data = new HashMap<>();
                                data.put("email", email);
                                data.put("role", "user"); // Role là dữ liệu bổ sung, mật khẩu không được lưu

                                db.child(uid).setValue(data).addOnSuccessListener(a -> {
                                    Toast.makeText(RegisterActivity.this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();

                                    // Chuyển thẳng sang màn hình thiết lập thiết bị
                                    startActivity(new Intent(RegisterActivity.this, DeviceSetupActivity.class));
                                    finish();
                                });

                            } else {
                                Toast.makeText(RegisterActivity.this, "Đăng ký thất bại: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        });
    }
}