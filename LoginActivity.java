package com.best.pickupnow;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText etEmail, etPassword, etName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d("GIGACHAD", "자동 로그인 감지. 권한 조회 중...");
            checkUserApprovalStatus(currentUser.getUid());
            return;
        }

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etName = findViewById(R.id.et_name);

        Button btnLogin = findViewById(R.id.btn_login);
        Button btnSignup = findViewById(R.id.btn_signup);

        btnLogin.setOnClickListener(v -> {
            loginUser();
        });

        btnSignup.setOnClickListener(v -> {
            createAccount();
        });
    }

    //  가입 시 실명과 시간을 DB에 저장
    private void createAccount() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // 방어 로직: 이름 입력 확인
        String name = "이름 없음";
        if (etName != null) {
            name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "가입자 실명을 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String pwPattern = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$";

        if (email.isEmpty()) {
            Toast.makeText(this, "이메일을 입력해라.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.matches(pwPattern)) {
            Toast.makeText(this, "비번은 영문+숫자+특수문자 8자리 이상.", Toast.LENGTH_LONG).show();
            return;
        }

        final String finalName = name; // 람다식 내부 사용을 위해 final 선언

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();

                        // 현재 가입 시간 생성
                        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("email", email);
                        userData.put("role", "PARENT");
                        userData.put("status", "PENDING");
                        userData.put("name", finalName); // 실명 저장
                        userData.put("request_time", currentTime); // 가입 시간 저장

                        db.collection("users").document(user.getUid())
                                .set(userData)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "가입 완료! 원장 승인을 기다리세요.", Toast.LENGTH_LONG).show();
                                    mAuth.signOut();
                                });
                    } else {
                        Toast.makeText(this, "가입 실패: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "이메일이나 비밀번호를 채워라.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        checkUserApprovalStatus(user.getUid());
                    } else {
                        Toast.makeText(this, "계정 정보가 틀렸습니다.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void checkUserApprovalStatus(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String status = document.getString("status");
                        String role = document.getString("role");

                        // 원장(ADMIN)은 무조건 프리패스, 나머지는 APPROVED여야 통과
                        if ("APPROVED".equals(status) || "ADMIN".equals(role)) {
                            Log.d("GIGACHAD", "승인 유저 라우팅: " + role);
                            Intent intent;
                            if ("ADMIN".equals(role)) {
                                intent = new Intent(this, AdminActivity.class);
                            } else if ("TEACHER".equals(role)) {
                                intent = new Intent(this, TeacherActivity.class);
                            } else {
                                intent = new Intent(this, ParentActivity.class);
                            }
                            startActivity(intent);
                            finish(); // 로그인 화면 파괴
                        } else {
                            // 승인 대기 상태
                            mAuth.signOut();
                            Toast.makeText(this, "미승인 계정입니다. 원장 승인을 기다리세요.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // ★ [버그 픽스] DB 문서 자체가 날아갔을 때 무반응 방지
                        Log.e("GIGACHAD", "DB에 유저 문서가 없음");
                        mAuth.signOut();
                        Toast.makeText(this, "계정 정보가 소실되었습니다. 다시 가입해주세요.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("GIGACHAD", "DB 조회 실패", e);
                    mAuth.signOut();
                    Toast.makeText(this, "서버 통신 에러가 발생했습니다.", Toast.LENGTH_SHORT).show();
                });
    }
}