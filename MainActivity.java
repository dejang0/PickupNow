package com.best.pickupnow;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 학부모 화면 다이렉트 점프
        findViewById(R.id.btn_role_parent).setOnClickListener(v -> {
            Toast.makeText(this, "학부모 데모 모드 진입", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, ParentActivity.class));
        });

        // 2. 교사 화면 다이렉트 점프
        findViewById(R.id.btn_role_teacher).setOnClickListener(v -> {
            Toast.makeText(this, "교사 데모 모드 진입", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, TeacherActivity.class));
        });

        // ★ [이스터에그] 교사 버튼을 '길게 꾹' 누르면 30명 생성
        findViewById(R.id.btn_role_teacher).setOnLongClickListener(v -> {
            injectDummyData();
            return true;
        });

        // 3. 원장 화면 다이렉트 점프
        findViewById(R.id.btn_role_admin).setOnClickListener(v -> {
            Toast.makeText(this, "원장 데모 모드 진입", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, AdminActivity.class));
        });

        // 4. 정식 로그인 화면 점프
        findViewById(R.id.btn_go_login).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });
    }

    // =========================================================
    // [더미 데이터 자동 주입기 (30명)]
    // =========================================================
    private void injectDummyData() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // 5살반, 6살반, 7살반, 특수반 총 30인 더미 데이터
        String[][] dummies = {
                // 5살반 (새싹)
                {"새싹1반", "김지훈", "ARRIVED"}, {"새싹1반", "이서아", "APPROACHING"}, {"새싹1반", "박도윤", "WAITING_MORNING"},
                {"새싹2반", "최하은", "ATTENDED"}, {"새싹2반", "정시우", "APPROACHING"}, {"새싹2반", "강지아", "ARRIVED"},
                {"새싹3반", "조민준", "WAITING_MORNING"}, {"새싹3반", "윤서연", "ATTENDED"}, {"새싹3반", "장건우", "APPROACHING"},
                // 6살반 (꽃잎)
                {"꽃잎1반", "임지안", "ARRIVED"}, {"꽃잎1반", "한서진", "ARRIVED"}, {"꽃잎1반", "오수아", "WAITING_MORNING"},
                {"꽃잎2반", "서도현", "APPROACHING"}, {"꽃잎2반", "신유주", "ATTENDED"}, {"꽃잎2반", "권은우", "ARRIVED"},
                {"꽃잎3반", "황연우", "WAITING_MORNING"}, {"꽃잎3반", "안태양", "APPROACHING"}, {"꽃잎3반", "송시아", "ARRIVED"},
                // 7살반 (열매)
                {"열매1반", "전은서", "ATTENDED"}, {"열매1반", "유지호", "ARRIVED"}, {"열매1반", "고하율", "APPROACHING"}, {"열매1반", "문지민", "WAITING_MORNING"},
                {"열매2반", "배윤아", "ARRIVED"}, {"열매2반", "백우진", "ATTENDED"}, {"열매2반", "허수빈", "APPROACHING"}, {"열매2반", "남기태", "ARRIVED"},
                // 특수반 (산새)
                {"산새1반", "심단우", "WAITING_MORNING"}, {"산새1반", "노다인", "APPROACHING"},
                {"산새2반", "하태민", "ARRIVED"}, {"산새2반", "곽지율", "ATTENDED"}
        };

        for (String[] dummy : dummies) {
            String cls = dummy[0];
            String name = dummy[1];
            String status = dummy[2];
            String docId = cls + "_" + name;

            Map<String, Object> data = new HashMap<>();
            data.put("class_name", cls);
            data.put("student_name", name);
            data.put("status", status);
            data.put("date", todayDate);
            data.put("updated_at", FieldValue.serverTimestamp());

            if (status.equals("APPROACHING")) {
                data.put("eta", "약 15분");
                data.put("start_time", FieldValue.serverTimestamp());
            } else {
                data.put("eta", "");
            }

            batch.set(db.collection("pickup_status").document(docId), data);
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, " 30명 더미 생성 완료 ", Toast.LENGTH_LONG).show();
        }).addOnFailureListener(e -> {
            Log.e("GIGACHAD", "생성 실패", e);
        });
    }
}