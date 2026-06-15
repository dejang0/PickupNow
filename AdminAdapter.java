package com.best.pickupnow;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;

public class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.ViewHolder> {
    private List<UserModel> list;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    public AdminAdapter(List<UserModel> list) {
        this.list = list;
    }

    public void updateList(List<UserModel> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserModel user = list.get(position);

        // 이메일 칸에 '이름(이메일)' 형식으로 노출
        holder.tvEmail.setText(user.getName() + " (" + user.getEmail() + ")");

        // 역할 칸에 '역할 - 가입시간' 형식으로 노출
        String roleText = user.getRole().equals("TEACHER") ? "교사" : "학부모";
        holder.tvRole.setText(roleText + " 가입 요청 | " + user.getRequestTime());

        holder.btnApprove.setOnClickListener(v -> showApprovalDialog(v, user));
    }

    private void showApprovalDialog(View v, UserModel user) {
        new AlertDialog.Builder(v.getContext())
                .setTitle("가입 승인")
                .setMessage("이름: " + user.getName() + "\n이메일: " + user.getEmail() + "\n가입시간: " + user.getRequestTime() + "\n\n해당 사용자의 가입을 승인하시겠습니까?")
                .setPositiveButton("승인하기", (dialog, which) -> {
                    db.collection("users").document(user.getId())
                            .update("status", "APPROVED")
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(v.getContext(), user.getName() + "님 승인 완료", Toast.LENGTH_SHORT).show()
                            );
                })
                .setNegativeButton("취소", null)
                .show();
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmail, tvRole;
        Button btnApprove;

        ViewHolder(View v) {
            super(v);
            tvEmail = v.findViewById(R.id.tv_email);
            tvRole = v.findViewById(R.id.tv_role);
            btnApprove = v.findViewById(R.id.btn_approve);
        }
    }
}