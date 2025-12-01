package com.example.tuoicayiot;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnSuccessListener; // Thư viện mới
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;

public class RegisterActivity extends AppCompatActivity {

    // Đổi tên biến để phản ánh việc sử dụng Email
    EditText etEmail, etPassword;
    Button btnRegister, btnMoveLogin;
    Spinner spRole;

    private FirebaseAuth mAuth;
    DatabaseReference userDbRef; // Đổi tên thành userDbRef

    // URL database của bạn (Cần thiết cho khởi tạo Firebase)
    private static final String DATABASE_URL = "https://smartwatering-7f76b-default-rtdb.asia-southeast1.firebasedatabase.app/";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Giả sử ID trong XML của bạn là etUsername,
        // nhưng chúng ta sẽ dùng nó để nhập Email
        etEmail = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        btnMoveLogin = findViewById(R.id.btnMoveLogin);
        spRole = findViewById(R.id.spRole);

        mAuth = FirebaseAuth.getInstance();
        // SỬ DỤNG getInstance CÓ URL NẾU CẦN THIẾT
        userDbRef = FirebaseDatabase.getInstance(DATABASE_URL).getReference("users");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Manager", "user"} // Đổi "Customer" thành "user" để đồng bộ với MainActivity
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRole.setAdapter(adapter);

        // Nút quay lại (Giả định ID là btnMoveLogin)
        if(btnMoveLogin != null) {
            btnMoveLogin.setOnClickListener(v -> finish());
        }


        btnRegister.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();
            String role = spRole.getSelectedItem().toString().toLowerCase(); // Chuẩn hóa role

            if (email.isEmpty() || pass.isEmpty() || pass.length() < 6) {
                Toast.makeText(this, "Vui lòng nhập Email và Mật khẩu (tối thiểu 6 ký tự)!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Bước 1: Tạo tài khoản bằng Firebase Authentication (dùng Email)
            mAuth.createUserWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user == null) return; // Bảo vệ

                            String uid = user.getUid();

                            // Bước 2: LƯU ROLE VÀO REALTIME DATABASE dưới node UID
                            HashMap<String, Object> data = new HashMap<>();
                            data.put("role", role);

                            userDbRef.child(uid).setValue(data)
                                    .addOnSuccessListener(a -> {
                                        Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
                                        finish(); // Quay lại màn hình Login
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(this, "Đăng ký thành công (Auth) nhưng lỗi lưu Role (DB): " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        // Vẫn finish() vì Auth đã thành công
                                        finish();
                                    });

                        } else {
                            // Xử lý lỗi đăng ký từ Firebase Auth
                            Toast.makeText(this, "Đăng ký thất bại: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}