package com.best.pickupnow;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Pattern;

public class ProfileActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private LinearLayout layoutChildrenContainer;
    private String currentUserUid; // 현재 로그인한 유저의 고유 ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // 유저 UID 가져오기
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            currentUserUid = "UNKNOWN_USER";
        }

        prefs = getSharedPreferences("MyProfile", MODE_PRIVATE);
        layoutChildrenContainer = findViewById(R.id.layout_children_container);

        EditText etChildName = findViewById(R.id.et_child_name);
        EditText etClassName = findViewById(R.id.et_class_name);
        Button btnSave = findViewById(R.id.btn_save_profile);

        InputFilter nameFilter = (source, start, end, dest, dstart, dend) -> {
            Pattern pattern = Pattern.compile("^[a-zA-Z가-힣ㄱ-ㅎㅏ-ㅣ\\s]*$");
            if (!pattern.matcher(source).matches()) return "";
            return null;
        };
        etChildName.setFilters(new InputFilter[]{nameFilter});

        btnSave.setOnClickListener(v -> {
            String childName = etChildName.getText().toString().trim();
            String className = etClassName.getText().toString().trim();

            if(childName.isEmpty() || className.isEmpty()) {
                Toast.makeText(this, "빈칸을 모두 채우세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                //  UID가 박힌 전용 열쇠로 데이터 열기
                String listKey = "childrenList_" + currentUserUid;
                String childrenJson = prefs.getString(listKey, "[]");
                JSONArray array = new JSONArray(childrenJson);

                for (int i = 0; i < array.length(); i++) {
                    if (array.getJSONObject(i).getString("name").equals(childName)) {
                        Toast.makeText(this, "이미 등록된 자녀입니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                JSONObject newChild = new JSONObject();
                newChild.put("name", childName);
                newChild.put("cls", className);
                array.put(newChild);

                // UID가 박힌 전용 열쇠로 데이터 잠그기
                prefs.edit().putString(listKey, array.toString()).apply();

                etChildName.setText("");
                etClassName.setText("");
                Toast.makeText(this, childName + " 자녀가 추가되었습니다.", Toast.LENGTH_SHORT).show();

                renderChildrenList();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        renderChildrenList();
    }

    private void renderChildrenList() {
        layoutChildrenContainer.removeAllViews();

        try {
            //  UID가 박힌 전용 열쇠로 데이터 열기
            String listKey = "childrenList_" + currentUserUid;
            String childrenJson = prefs.getString(listKey, "[]");
            JSONArray array = new JSONArray(childrenJson);

            if (array.length() == 0) {
                TextView tvEmpty = new TextView(this);
                tvEmpty.setText("등록된 자녀가 없습니다.");
                tvEmpty.setTextColor(Color.GRAY);
                layoutChildrenContainer.addView(tvEmpty);
                return;
            }

            for (int i = 0; i < array.length(); i++) {
                JSONObject child = array.getJSONObject(i);
                String name = child.getString("name");
                String cls = child.getString("cls");
                final int index = i;

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, 20, 0, 20);

                TextView tvInfo = new TextView(this);
                tvInfo.setText(cls + " " + name);
                tvInfo.setTextSize(16f);
                tvInfo.setTextColor(Color.BLACK);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                tvInfo.setLayoutParams(textParams);

                Button btnDelete = new Button(this);
                btnDelete.setText("삭제");
                btnDelete.setTextColor(Color.WHITE);
                btnDelete.setBackgroundColor(Color.parseColor("#F44336"));

                btnDelete.setOnClickListener(v -> deleteChild(index, name));

                row.addView(tvInfo);
                row.addView(btnDelete);
                layoutChildrenContainer.addView(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteChild(int index, String name) {
        new AlertDialog.Builder(this)
                .setTitle("자녀 삭제")
                .setMessage(name + " 자녀를 목록에서 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    try {
                        // UID가 박힌 전용 열쇠로 데이터 열기
                        String listKey = "childrenList_" + currentUserUid;
                        String childrenJson = prefs.getString(listKey, "[]");
                        JSONArray oldArray = new JSONArray(childrenJson);
                        JSONArray newArray = new JSONArray();

                        for (int i = 0; i < oldArray.length(); i++) {
                            if (i != index) {
                                newArray.put(oldArray.getJSONObject(i));
                            }
                        }

                        // UID가 박힌 전용 열쇠로 데이터 잠그기
                        prefs.edit().putString(listKey, newArray.toString()).apply();
                        Toast.makeText(this, name + " 삭제 완료", Toast.LENGTH_SHORT).show();
                        renderChildrenList();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }
}