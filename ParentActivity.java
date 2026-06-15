package com.best.pickupnow;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ParentActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private Button btnMorningDrop, btnApproaching, btnParentUndo;
    private TextView tvStatusDisplay, tvLiveClock;
    private Spinner spinnerChildren;
    private String todayDate;

    private List<String> childNames = new ArrayList<>();
    private List<String> classNames = new ArrayList<>();
    private String currentSelectedChild = "";
    private String currentSelectedClass = "";

    private ListenerRegistration currentListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        db = FirebaseFirestore.getInstance();
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        btnMorningDrop = findViewById(R.id.btn_morning_drop);
        btnApproaching = findViewById(R.id.btn_approaching);
        btnParentUndo = findViewById(R.id.btn_parent_undo);
        tvStatusDisplay = findViewById(R.id.tv_status_display);
        spinnerChildren = findViewById(R.id.spinner_children);
        tvLiveClock = findViewById(R.id.tv_live_clock); //  라이브 시계 연결

        startLiveClock(); //  1초마다 돌아가는 시계 엔진 가동

        Button btnGoProfile = findViewById(R.id.btn_go_profile);
        btnGoProfile.setOnClickListener(v -> startActivity(new Intent(ParentActivity.this, ProfileActivity.class)));
        btnGoProfile.setOnLongClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(ParentActivity.this, LoginActivity.class));
            finish();
            return true;
        });

        // 아침 등원 버튼 클릭 (시간 통제 방어막 탑재)
        btnMorningDrop.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY); // 0 ~ 23시간 포맷

            // 아침 7시(7)부터 오후 5시(17) 전까지만 등원 허용
            if (hour < 7 || hour >= 17) {
                new AlertDialog.Builder(this)
                        .setTitle("등원 시간 종료")
                        .setMessage("현재는 유치원 등원 가능 시간(07:00 ~ 17:00)이 아닙니다.\n특이사항이 있다면 유치원으로 직접 연락해주세요.")
                        .setPositiveButton("확인", null)
                        .show();
                return; // 실행 즉시 컷
            }
            updateStatus("ATTENDED", "");
        });

        btnApproaching.setOnClickListener(v -> updateStatus("APPROACHING", "15분"));

        btnParentUndo.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("실행 취소")
                    .setMessage("가장 최근 상태를 취소하시겠습니까?")
                    .setPositiveButton("예", (dialog, which) -> {
                        String currentText = tvStatusDisplay.getText().toString();
                        if (currentText.contains("이동 중 - 도착 15분 전")) {
                            updateStatus("ATTENDED", "");
                        } else {
                            updateStatus("WAITING_MORNING", "");
                        }
                    })
                    .setNegativeButton("아니오", null)
                    .show();
        });

        loadChildrenData();
    }

    // [실시간 시계 엔진] 1초마다 메인 스레드에서 UI를 갱신
    private void startLiveClock() {
        if (tvLiveClock == null) return;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                String currentTime = new SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss", Locale.getDefault()).format(new Date());
                tvLiveClock.setText(currentTime);
                handler.postDelayed(this, 1000); // 1초 뒤에 자기 자신을 다시 호출
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadChildrenData();
    }

    private void loadChildrenData() {
        SharedPreferences prefs = getSharedPreferences("MyProfile", MODE_PRIVATE);

        // 현재 로그인한 유저의 UID를 섞어서 열쇠를 만든다
        String currentUserUid = "UNKNOWN_USER";
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        String listKey = "childrenList_" + currentUserUid;

        //  그 열쇠로 데이터 불러오기
        String childrenJson = prefs.getString(listKey, "[]");

        childNames.clear();
        classNames.clear();

        try {
            JSONArray array = new JSONArray(childrenJson);
            for(int i = 0; i < array.length(); i++) {
                childNames.add(array.getJSONObject(i).getString("name"));
                classNames.add(array.getJSONObject(i).getString("cls"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (childNames.isEmpty()) {
            tvStatusDisplay.setText("자녀를 먼저 등록하세요");
            btnMorningDrop.setVisibility(View.GONE);
            btnApproaching.setVisibility(View.GONE);
            btnParentUndo.setVisibility(View.GONE);

            // 등록된 자녀가 없으면 스피너도 비워버린다
            spinnerChildren.setAdapter(null);
            currentSelectedChild = "";
            currentSelectedClass = "";
            return;
        }

        List<String> displayList = new ArrayList<>();
        for(int i = 0; i < childNames.size(); i++) {
            displayList.add(classNames.get(i) + " " + childNames.get(i));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerChildren.setAdapter(adapter);

        spinnerChildren.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentSelectedChild = childNames.get(position);
                currentSelectedClass = classNames.get(position);
                checkDateAndReset();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // 1. 날짜 체크 및 초기화 메서드
    private void checkDateAndReset() {
        // 아이 이름이나 반 이름이 없으면 튕겨냄
        if (currentSelectedChild.isEmpty() || currentSelectedClass.isEmpty()) return;

        // 복합키 문서 ID 생성 (예: "새싹1반_박지훈")
        String docId = currentSelectedClass + "_" + currentSelectedChild;

        // 복합키 ID로 DB 조회
        db.collection("pickup_status").document(docId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String dbDate = doc.getString("date");
                        if (dbDate == null || !dbDate.equals(todayDate)) {
                            Log.d("GIGACHAD", "날짜 변경됨. 자정 초기화 발동!");
                            updateStatus("WAITING_MORNING", "");
                        } else {
                            listenToMyChildStatus();
                        }
                    } else {
                        updateStatus("WAITING_MORNING", "");
                    }
                });
    }

    // 2. 상태 업데이트 (DB에 쏘는 메서드)
    private void updateStatus(String status, String eta) {
        if (currentSelectedChild.isEmpty() || currentSelectedClass.isEmpty()) return;

        // 복합키 문서 ID 생성
        String docId = currentSelectedClass + "_" + currentSelectedChild;

        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        data.put("eta", eta);
        data.put("updated_at", FieldValue.serverTimestamp());
        data.put("student_name", currentSelectedChild);
        data.put("class_name", currentSelectedClass);
        data.put("date", todayDate);

        if ("APPROACHING".equals(status)) {
            data.put("start_time", FieldValue.serverTimestamp());
        }

        //  복합키 ID를 가진 문서에 덮어쓰기
        db.collection("pickup_status").document(docId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    listenToMyChildStatus();
                });
    }

    // 3. 내 아이 상태 실시간 추적 리스너
    private void listenToMyChildStatus() {
        if (currentSelectedChild.isEmpty() || currentSelectedClass.isEmpty()) return;

        if (currentListener != null) currentListener.remove();

        // 복합키 문서 ID 생성
        String docId = currentSelectedClass + "_" + currentSelectedChild;

        //  복합키 ID 문서를 실시간 감시
        currentListener = db.collection("pickup_status").document(docId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;

                    String status = snapshot.getString("status");

                    // UI 업데이트 로직은 기존과 100% 동일
                    if ("ARRIVED".equals(status)) {
                        btnMorningDrop.setVisibility(View.GONE);
                        btnApproaching.setVisibility(View.VISIBLE);
                        btnApproaching.setEnabled(false);
                        btnApproaching.setText("오늘 하원 프로세스 종료");
                        btnApproaching.setBackgroundColor(Color.GRAY);
                        btnParentUndo.setVisibility(View.GONE);

                        tvStatusDisplay.setText("안전 인계 완료");
                        tvStatusDisplay.setTextColor(Color.parseColor("#4CAF50"));
                    } else if ("APPROACHING".equals(status)) {
                        btnMorningDrop.setVisibility(View.GONE);
                        btnApproaching.setVisibility(View.VISIBLE);
                        btnApproaching.setEnabled(false);
                        btnApproaching.setText("유치원으로 이동 중");
                        btnParentUndo.setVisibility(View.VISIBLE);

                        tvStatusDisplay.setText("하원 이동 중");
                        tvStatusDisplay.setTextColor(Color.parseColor("#FF9800"));
                    } else if ("ATTENDED".equals(status)) {
                        btnMorningDrop.setVisibility(View.GONE);
                        btnApproaching.setVisibility(View.VISIBLE);
                        btnApproaching.setEnabled(true);
                        btnApproaching.setText("유치원으로 출발 (하원)");
                        btnApproaching.setBackgroundColor(Color.parseColor("#FFC107"));
                        btnParentUndo.setVisibility(View.VISIBLE);

                        tvStatusDisplay.setText("등원 완료 (원내 활동중)");
                        tvStatusDisplay.setTextColor(Color.parseColor("#03A9F4"));
                    } else {
                        btnMorningDrop.setVisibility(View.VISIBLE);
                        btnMorningDrop.setEnabled(true);
                        btnApproaching.setVisibility(View.GONE);
                        btnParentUndo.setVisibility(View.GONE);

                        tvStatusDisplay.setText("등원 전 대기");
                        tvStatusDisplay.setTextColor(Color.parseColor("#757575"));
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentListener != null) currentListener.remove();
    }
}