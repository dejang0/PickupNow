package com.best.pickupnow; // 네 패키지명 확인해라

public class PickUpModel {
    private String studentId;
    private String studentName;
    private String status;
    private String eta;

    public PickUpModel() {} // Firebase 필수 빈 생성자

    public PickUpModel(String studentId, String studentName, String status, String eta) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.status = status;
        this.eta = eta;
    }

    public String getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public String getStatus() { return status; }
    public String getEta() { return eta; }
}