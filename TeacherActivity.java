package com.best.pickupnow;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TeacherActivity extends AppCompatActivity {
    private TeacherAdapter adapter;
    private FirebaseFirestore db;

    // UI 객체들
    private TextView tvLiveClock;
    private TextView tvCountWaiting, tvCountApproaching, tvCountArrived;

    // 시각화 UI
    private ProgressBar progressDismissal;
    private TextView tvProgressPercent, tvCongestionWarning;

    // 필터 제어용
    private LinearLayout layoutClassFilters;
    private String currentClassFilter = "전체";
    private List<QueryDocumentSnapshot> rawDataList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher);

        // UI 바인딩
        tvLiveClock = findViewById(R.id.tv_live_clock);
        tvCountWaiting = findViewById(R.id.tv_count_waiting);
        tvCountApproaching = findViewById(R.id.tv_count_approaching);
        tvCountArrived = findViewById(R.id.tv_count_arrived);
        layoutClassFilters = findViewById(R.id.layout_class_filters);

        progressDismissal = findViewById(R.id.progress_dismissal);
        tvProgressPercent = findViewById(R.id.tv_progress_percent);
        tvCongestionWarning = findViewById(R.id.tv_congestion_warning);

        startLiveClock();

        findViewById(R.id.tv_teacher_title).setOnLongClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Toast.makeText(this, "교사 세션 파괴! 로그아웃 완료", Toast.LENGTH_SHORT).show();
            startActivity(new android.content.Intent(this, LoginActivity.class));
            finish();
            return true;
        });

        RecyclerView rv = findViewById(R.id.rv_teacher);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TeacherAdapter(new ArrayList<>());

        adapter.setOnItemLongClickListener(model -> {
            triggerAIReportForStudent(model.getStudentId(), model.getStudentName());
        });

        rv.setAdapter(adapter);
        db = FirebaseFirestore.getInstance();
        listenToStatus();
    }

    private void startLiveClock() {
        if (tvLiveClock == null) return;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                tvLiveClock.setText(new SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss", Locale.getDefault()).format(new Date()));
                handler.postDelayed(this, 1000);
            }
        });
    }

    private void listenToStatus() {
        db.collection("pickup_status")
                .orderBy("updated_at", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    rawDataList.clear();
                    for (QueryDocumentSnapshot doc : value) {
                        rawDataList.add(doc);
                    }
                    renderClassFilters();
                    applyFilterAndUpdateUI();
                });
    }

    private void renderClassFilters() {
        layoutClassFilters.removeAllViews();
        List<String> uniqueClasses = new ArrayList<>();
        uniqueClasses.add("전체");

        for (QueryDocumentSnapshot doc : rawDataList) {
            String cName = doc.getString("class_name");
            if (cName != null && !cName.isEmpty() && !uniqueClasses.contains(cName)) {
                uniqueClasses.add(cName);
            }
        }

        for (String className : uniqueClasses) {
            Button btn = new Button(this);
            btn.setText(className);

            if (className.equals(currentClassFilter)) {
                btn.setBackgroundColor(Color.parseColor("#03A9F4"));
                btn.setTextColor(Color.WHITE);
            } else {
                btn.setBackgroundColor(Color.parseColor("#E0E0E0"));
                btn.setTextColor(Color.BLACK);
            }

            btn.setOnClickListener(v -> {
                currentClassFilter = className;
                renderClassFilters();
                applyFilterAndUpdateUI();
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 15, 0);
            btn.setLayoutParams(params);
            layoutClassFilters.addView(btn);
        }
    }

    // 리스트 분배 및 실시간 시각화 게이지 연산
    private void applyFilterAndUpdateUI() {
        List<PickUpModel> newList = new ArrayList<>();
        int waitingOrAttended = 0, approaching = 0, arrived = 0;

        for (QueryDocumentSnapshot doc : rawDataList) {
            String cName = doc.getString("class_name");
            if (cName == null) cName = "미분류";

            if (!currentClassFilter.equals("전체") && !currentClassFilter.equals(cName)) {
                continue;
            }

            String status = doc.getString("status");
            if (status == null) status = "WAITING_MORNING";

            String displayName = doc.getString("student_name");
            if (currentClassFilter.equals("전체")) {
                displayName = "[" + cName + "] " + displayName;
            }

            newList.add(new PickUpModel(doc.getId(), displayName, status, doc.getString("eta")));

            switch (status) {
                case "APPROACHING": approaching++; break;
                case "ARRIVED": arrived++; break;
                case "ATTENDED":
                case "WAITING_MORNING":
                default: waitingOrAttended++; break;
            }
        }

        adapter.updateList(newList);
        tvCountWaiting.setText(String.valueOf(waitingOrAttended));
        tvCountApproaching.setText(String.valueOf(approaching));
        tvCountArrived.setText(String.valueOf(arrived));

        // [시각적 분석 로직] 하원 완료율 계산 및 게이지 색상 변경
        int totalKids = waitingOrAttended + approaching + arrived;
        int percent = totalKids > 0 ? (int) (((float) arrived / totalKids) * 100) : 0;

        progressDismissal.setProgress(percent);
        tvProgressPercent.setText(percent + "%");

        if (approaching >= 3) {
            tvCongestionWarning.setText("🔴 혼잡도: 경고 (" + approaching + "명 동시 이동 중!)");
            tvCongestionWarning.setTextColor(Color.parseColor("#D32F2F"));
            tvCongestionWarning.setBackgroundColor(Color.parseColor("#FFEBEE"));
        } else if (approaching >= 1) {
            tvCongestionWarning.setText("🟡 혼잡도: 주의 (" + approaching + "명 이동 중)");
            tvCongestionWarning.setTextColor(Color.parseColor("#E65100"));
            tvCongestionWarning.setBackgroundColor(Color.parseColor("#FFF3E0"));
        } else {
            tvCongestionWarning.setText("🟢 혼잡도: 원활 (이동 중인 학부모 없음)");
            tvCongestionWarning.setTextColor(Color.parseColor("#2E7D32"));
            tvCongestionWarning.setBackgroundColor(Color.parseColor("#E8F5E9"));
        }
    }

    private void triggerAIReportForStudent(String studentId, String studentName) {
        Toast.makeText(this, studentName + " 원아 데이터 수집 및 AI 분석 시작...", Toast.LENGTH_SHORT).show();

        db.collection("pickup_logs")
                .whereEqualTo("student_id", studentId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    StringBuilder rawDataBuilder = new StringBuilder();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String date = doc.getTimestamp("completed_at") != null ? doc.getTimestamp("completed_at").toDate().toString() : "날짜 불명";
                        Long duration = doc.getLong("duration_seconds");
                        String reason = doc.contains("reason") ? doc.getString("reason") : "정상 하원";
                        rawDataBuilder.append("[날짜: ").append(date)
                                .append(", 하원 소요시간: ").append(duration).append("초")
                                .append(", 사유/특이사항: ").append(reason).append("]\n");
                    }

                    String rawData = rawDataBuilder.toString();
                    if (rawData.isEmpty()) {
                        Toast.makeText(this, "AI 분석을 위한 과거 데이터가 없습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    generateGeminiAIReport(studentName, rawData);
                })
                .addOnFailureListener(e -> Log.e("GIGACHAD_AI", "DB 로그 읽기 실패", e));
    }

    private void generateGeminiAIReport(String childName, String rawData) {
        new Thread(() -> {
            try {
                String apiKey = "AQ.Ab8RN6JRYkupfY3GTmTg4NGHuqZLMeK8v9cNGDRiNYIuLYwz-Q";
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + apiKey);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String prompt = "너는 유치원 원장님과 교사를 보좌하는 교육 AI 비서다. " +
                        childName + " 원아의 데이터: " + rawData +
                        " 위 데이터를 분석하여 하원 패턴과 주의점을 3줄 요약해라.";

                JSONObject root = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject content = new JSONObject();
                content.put("role", "user");

                JSONArray parts = new JSONArray();
                JSONObject part = new JSONObject();
                part.put("text", prompt);
                parts.put(part);
                content.put("parts", parts);
                contents.put(content);
                root.put("contents", contents);

                try(OutputStream os = conn.getOutputStream()) {
                    byte[] input = root.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) response.append(line);

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String aiResult = jsonResponse.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");

                    runOnUiThread(() -> new AlertDialog.Builder(this)
                            .setTitle(childName + " AI 분석")
                            .setMessage(aiResult)
                            .setPositiveButton("확인", null)
                            .show());
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "서버 거절: " + responseCode, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e("GIGACHAD_AI", "통신 예외", e);
            }
        }).start();
    }
}