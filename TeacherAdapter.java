package com.best.pickupnow;

import android.app.AlertDialog;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeacherAdapter extends RecyclerView.Adapter<TeacherAdapter.ViewHolder> {
    private List<PickUpModel> list;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    // [AI 탑재] 롱클릭 감지를 위한 인터페이스
    public interface OnItemLongClickListener {
        void onItemLongClick(PickUpModel model);
    }
    private OnItemLongClickListener longClickListener;

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public TeacherAdapter(List<PickUpModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PickUpModel model = list.get(position);
        holder.tvName.setText(model.getStudentName());

        //기본 상태를 등원 전 대기(WAITING_MORNING)로 세팅
        String status = model.getStatus() != null ? model.getStatus() : "WAITING_MORNING";

        // [AI 탑재] 학생 카드 전체를 꾹 누르면 AI 분석 엔진으로 신호 줌
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(model);
            }
            return true;
        });

        // ================= [4단계 상태 라우팅 엔진] =================

        if (status.equals("APPROACHING")) { // 1. 오후 하원 이동 중
            holder.tvStatusDesc.setText("하원 이동중 (ETA: " + model.getEta() + ")");
            holder.tvStatusDesc.setTextColor(Color.parseColor("#E65100"));
            holder.layoutBg.setBackgroundColor(Color.parseColor("#FFF9C4"));

            holder.btnComplete.setVisibility(View.VISIBLE);
            holder.btnUndo.setVisibility(View.GONE);

            // 하원 인계 완료 처리 및 AI 로그 적재
            holder.btnComplete.setOnClickListener(v -> {
                db.collection("pickup_status").document(model.getStudentId())
                        .update("status", "ARRIVED");

                db.collection("pickup_status").document(model.getStudentId()).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists() && documentSnapshot.contains("start_time")) {
                                com.google.firebase.Timestamp startTime = documentSnapshot.getTimestamp("start_time");

                                if (startTime != null) {
                                    long startMillis = startTime.toDate().getTime();
                                    long endMillis = System.currentTimeMillis();
                                    long durationSeconds = (endMillis - startMillis) / 1000;

                                    Map<String, Object> logData = new HashMap<>();
                                    logData.put("student_id", model.getStudentId());
                                    logData.put("student_name", model.getStudentName());
                                    logData.put("duration_seconds", durationSeconds);
                                    logData.put("completed_at", FieldValue.serverTimestamp());

                                    db.collection("pickup_logs").add(logData)
                                            .addOnSuccessListener(docRef ->
                                                    Log.d("GIGACHAD", "AI 통계 로그 적재 완료: " + durationSeconds + "초 소요")
                                            );
                                }
                            }
                        });
            });

        } else if (status.equals("ARRIVED")) { // 2. 하원 인계 완료
            holder.tvStatusDesc.setText("인계 완료");
            holder.tvStatusDesc.setTextColor(Color.parseColor("#2E7D32"));
            holder.layoutBg.setBackgroundColor(Color.parseColor("#E8F5E9"));

            holder.btnComplete.setVisibility(View.GONE);
            holder.btnUndo.setVisibility(View.VISIBLE);

            holder.btnUndo.setOnClickListener(v -> {
                new AlertDialog.Builder(v.getContext())
                        .setTitle("인계 완료 취소")
                        .setMessage("정말 하원 완료 처리를 취소하시겠습니까?\n학부모 화면이 다시 '이동 중'으로 되돌아갑니다.")
                        .setPositiveButton("예, 취소합니다", (dialog, which) -> {
                            db.collection("pickup_status").document(model.getStudentId())
                                    .update("status", "APPROACHING")
                                    .addOnSuccessListener(aVoid -> Toast.makeText(v.getContext(), "상태가 롤백되었습니다.", Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("아니오", null)
                        .show();
            });

        } else if (status.equals("ATTENDED")) { // 3. 아침 등원 완료
            holder.tvStatusDesc.setText("등원 완료 (원내 활동중)");
            holder.tvStatusDesc.setTextColor(Color.parseColor("#03A9F4"));
            holder.layoutBg.setBackgroundColor(Color.parseColor("#E1F5FE")); // 연한 파란색 배경

            holder.btnComplete.setVisibility(View.GONE);
            holder.btnUndo.setVisibility(View.GONE);

        } else { // 4. 아침 기상 / 등원 전 대기 (WAITING_MORNING 또는 WAITING)
            holder.tvStatusDesc.setText("등원 전 대기");
            holder.tvStatusDesc.setTextColor(Color.parseColor("#757575"));
            holder.layoutBg.setBackgroundColor(Color.parseColor("#FFFFFF"));

            holder.btnComplete.setVisibility(View.GONE);
            holder.btnUndo.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStatusDesc;
        LinearLayout layoutBg;
        Button btnComplete, btnUndo;

        ViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_name);
            tvStatusDesc = v.findViewById(R.id.tv_status_desc);
            layoutBg = v.findViewById(R.id.layout_bg);
            btnComplete = v.findViewById(R.id.btn_teacher_complete);
            btnUndo = v.findViewById(R.id.btn_teacher_undo);
        }
    }

    // 리스트를 통째로 갈아끼우고 화면을 새로고침하는 메서드
    public void updateList(List<PickUpModel> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }
}