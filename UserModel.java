package com.best.pickupnow;

public class UserModel {
    private String id;
    private String email;
    private String role;
    private String status;
    private String name;
    private String requestTime;

    public UserModel(String id, String email, String role, String status, String name, String requestTime) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.status = status;
        this.name = name;
        this.requestTime = requestTime;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getName() { return name != null ? name : "이름 없음"; }
    public String getRequestTime() { return requestTime != null ? requestTime : "시간 불명"; }
}