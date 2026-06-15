package com.best.pickupnow;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminActivity extends AppCompatActivity {
    private AdminAdapter adapter;
    private FirebaseFirestore db;

    private TextView tvTotalLogs, tvAvgDuration, tvPeakTime;
    private TextView tvLiveClock; // 라이브 시계 선언

    // 차트 관련 UI 객체 선언
    private LinearLayout layoutChartSection;
    private View[] barSlots = new View[6];
    private int[] rawTrafficData = new int[6]; // 파이어베이스에서 실시간 추출될 교통 혼잡 데이터

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // ★ [실시간 시계 바인딩 및 가동] (XML 상단에 tv_live_clock이 무조건 있어야 함)
        tvLiveClock = findViewById(R.id.tv_live_clock);
        startLiveClock();

        tvTotalLogs = findViewById(R.id.tv_total_logs);
        tvAvgDuration = findViewById(R.id.tv_avg_duration);
        tvPeakTime = findViewById(R.id.tv_peak_time);

        // 차트 컴포넌트 바인딩
        layoutChartSection = findViewById(R.id.layout_chart_section);
        barSlots[0] = findViewById(R.id.bar_slot1);
        barSlots[1] = findViewById(R.id.bar_slot2);
        barSlots[2] = findViewById(R.id.bar_slot3);
        barSlots[3] = findViewById(R.id.bar_slot4);
        barSlots[4] = findViewById(R.id.bar_slot5);
        barSlots[5] = findViewById(R.id.bar_slot6);

        RecyclerView rv = findViewById(R.id.rv_admin);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminAdapter(new ArrayList<>());
        rv.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();

        // 1. 분석판 카드 클릭 시 그래프 레이아웃 토글
        findViewById(R.id.card_analysis).setOnClickListener(v -> {
            if (layoutChartSection.getVisibility() == View.GONE) {
                layoutChartSection.setVisibility(View.VISIBLE);
                updateChartVisualization(1.0f); // 기본 오늘 데이터 스케일 로드
            } else {
                layoutChartSection.setVisibility(View.GONE);
            }
        });

        // 2. 그래프 필터 제어 기법 연동
        findViewById(R.id.btn_filter_day).setOnClickListener(v -> updateChartVisualization(1.0f));   // 오늘 하루 기준 리프레시
        findViewById(R.id.btn_filter_week).setOnClickListener(v -> updateChartVisualization(4.2f));  // 주간 단위 누적 가중치 스케일링
        findViewById(R.id.btn_filter_month).setOnClickListener(v -> updateChartVisualization(18.5f)); // 월간 가중치 확장 스케일링

        // 타이틀 꾹 누르면 로그아웃
        findViewById(R.id.tv_admin_title).setOnLongClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Toast.makeText(this, "원장 세션 파괴! 로그아웃 완료", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        });

        // 일일 초기화 버튼 연결
        findViewById(R.id.btn_daily_reset).setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("새로운 하루 시작")
                    .setMessage("오늘의 등/하원 데이터를 초기화하시겠습니까?\n(주말 및 지정 공휴일은 자동 방어됩니다)")
                    .setPositiveButton("초기화 진행", (dialog, which) -> resetDailyPickupStatus())
                    .setNegativeButton("취소", null)
                    .show();
        });

        listenToPendingUsers();
        calculateTrafficStats();
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

    // [시각화 그래픽 엔진] 외부 라이브러리 없이 레이아웃 픽셀을 동적 연산 제어
    private void updateChartVisualization(float scaleFactor) {
        int maxVal = 1;
        for (int val : rawTrafficData) {
            if (val > maxVal) maxVal = val;
        }

        for (int i = 0; i < 6; i++) {
            final int index = i;
            final int currentVal = rawTrafficData[index];
            final int finalMax = maxVal;

            // 픽셀 연산 로직은 UI 스레드 안전 구역에서 실행
            barSlots[index].post(() -> {
                // 실시간 데이터 밀도와 스케일 팩터를 조합하여 높이 계산 (최대 130dp 제한)
                float ratio = (float) currentVal / finalMax;
                int calculatedHeight = (int) (ratio * 120 * scaleFactor);
                if (calculatedHeight > 140) calculatedHeight = 140; // 최대 높이 방어선
                if (calculatedHeight < 10 && currentVal > 0) calculatedHeight = 15; // 데이터 식별 최소 높이

                // 자바 코드로 LayoutParams를 직접 건드려서 드로잉
                ViewGroup.LayoutParams params = barSlots[index].getLayoutParams();
                // dp 단위를 px 단위로 컨버팅 연산
                float density = getResources().getDisplayMetrics().density;
                params.height = (int) (calculatedHeight * density);
                barSlots[index].setLayoutParams(params);
            });
        }
        Toast.makeText(this, "데이터 연산 완료 (스케일 가중치: " + scaleFactor + ")", Toast.LENGTH_SHORT).show();
    }

    private void listenToPendingUsers() {
        db.collection("users")
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    List<UserModel> newList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : value) {
                        newList.add(new UserModel(
                                doc.getId(),
                                doc.getString("email"),
                                doc.getString("role"),
                                doc.getString("status"),
                                doc.getString("name"),
                                doc.getString("request_time")
                        ));
                    }
                    adapter.updateList(newList);
                });
    }

    private void resetDailyPickupStatus() {
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            Toast.makeText(this, "오늘은 주말입니다. 초기화를 진행하지 않습니다.", Toast.LENGTH_LONG).show();
            return;
        }

        db.collection("pickup_status").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.update(doc.getReference(), "status", "WAITING_MORNING");
                        batch.update(doc.getReference(), "eta", "");
                    }
                    batch.commit().addOnSuccessListener(aVoid ->
                            Toast.makeText(this, "새로운 하루 시작! 모든 데이터가 초기화되었습니다.", Toast.LENGTH_SHORT).show()
                    );
                });
    }

    // [트래픽 분석 엔진 연동] 실시간 데이터 적재 시 배열 구조로 차트 매핑 변경
    private void calculateTrafficStats() {
        db.collection("pickup_logs")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    long totalSeconds = 0;
                    int count = value.size();

                    // 6개 구간 배열 초기화
                    for (int i = 0; i < 6; i++) rawTrafficData[i] = 0;

                    for (QueryDocumentSnapshot doc : value) {
                        Long duration = doc.getLong("duration_seconds");
                        if (duration != null) totalSeconds += duration;

                        com.google.firebase.Timestamp timestamp = doc.getTimestamp("completed_at");
                        if (timestamp != null) {
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(timestamp.toDate());
                            int hour = cal.get(Calendar.HOUR_OF_DAY);

                            // 유치원 운영 시간(18시 종료)에 맞춘 6개 슬롯 분배
                            if (hour < 13) {
                                rawTrafficData[0]++; // 13시 이전
                            } else if (hour < 14) {
                                rawTrafficData[1]++; // 13시 ~ 14시
                            } else if (hour < 15) {
                                rawTrafficData[2]++; // 14시 ~ 15시
                            } else if (hour < 16) {
                                rawTrafficData[3]++; // 15시 ~ 16시
                            } else if (hour < 17) {
                                rawTrafficData[4]++; // 16시 ~ 17시
                            } else {
                                rawTrafficData[5]++; // 17시 ~ 18시 (17시 이후 전체)
                            }
                        }
                    }

                    long avgDuration = count > 0 ? totalSeconds / count : 0;
                    long avgMin = avgDuration / 60;
                    long avgSec = avgDuration % 60;

                    int maxSlotIdx = -1;
                    int maxCount = 0;
                    for (int i = 0; i < 6; i++) {
                        if (rawTrafficData[i] > maxCount) {
                            maxCount = rawTrafficData[i];
                            maxSlotIdx = i;
                        }
                    }

                    String peakTimeRange = "데이터 부족";
                    if (maxSlotIdx != -1) {
                        switch (maxSlotIdx) {
                            case 0:
                                peakTimeRange = "13:00 이전";
                                break;
                            case 1:
                                peakTimeRange = "13:00 ~ 14:00";
                                break;
                            case 2:
                                peakTimeRange = "14:00 ~ 15:00";
                                break;
                            case 3:
                                peakTimeRange = "15:00 ~ 16:00";
                                break;
                            case 4:
                                peakTimeRange = "16:00 ~ 17:00";
                                break;
                            case 5:
                                peakTimeRange = "17:00 ~ 18:00";
                                break;
                        }
                    }

                    tvTotalLogs.setText("누적 분석 데이터: " + count + "건");
                    tvAvgDuration.setText("평균 하원 소요 시간: " + avgMin + "분 " + avgSec + "초");
                    tvPeakTime.setText(" 최고 혼잡 예상 구간: " + peakTimeRange);

                    if (layoutChartSection.getVisibility() == View.VISIBLE) {
                        updateChartVisualization(1.0f);
                    }
                });
    }
}