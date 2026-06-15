# PickUp Now - 유치원 실시간 하원 통제 및 관제 시스템
> **"학부모-교사-원장을 잇는 통신 딜레이 최소화 실시간 동기화 솔루션"**

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![Gemini](https://img.shields.io/badge/Gemini_3.5_Flash-8E75B2?style=for-the-badge&logo=googlebard&logoColor=white)

## 1. 프로젝트 개요
유치원 하원 시간대에는 차량과 학부모가 한꺼번에 몰려 발생하는 안전사고 가능성이 있습니다. 또 교사가 일일이 원아의 인계 상태를 수동으로 확인해야 하는 업무 병목 현상을 해결하기 위해 기획된 Android 기반 B2B SaaS 애플리케이션입니다. 

Firebase Firestore의 실시간 동기화를 활용하여 학부모의 출발 신호를 교사의 대시보드에 즉각적으로 반영하며 혼잡도를 최소화하는 통제 시스템을 제공합니다.

<br>

## 2. 데모 영상
[데모 시연 영상](https://youtu.be/RkEjScfWS1Y?si=3V_SIE5i7tK9wmpI)

<br>

## 3. 핵심 기능

### 학부모
- 기기 로컬(SharedPreferences)과 계정 UID를 결합하여 다자녀 프로필을 안전하게 관리합니다
- 등원, 유치원으로 출발, 인계 완료 등의 상태를 0.1초 내의 딜레이로 교사 화면으로 전송합니다.
- 심야 및 비업무 시간(17시 이후) 하원/등원 버튼 터치 시 자동 차단 로직 적용했습니다.

### 교사
- 반별 탭을 누를 때마다 화면 전환이나 서버 재호출 없이 로컬 메모리에서 즉각적으로 해당 반 원아만 필터링하여 렌더링하는 Single Page Dynamic Filtering 방법을 사용했습니다.
- 이동 중인 학부모가 3명 이상일 경우 대시보드 상단에 즉각적인 🔴 Red Warning 표출하는 실시간 혼잡도 경고 시스템으로 구성했습니다.
- 원아 리스트 롱클릭 시 Firestore에 적재된 하원 소요 시간 로그를 바탕으로 Google Gemini 3.5 flash가 특이사항을 3줄 요약하여 리포팅하는 AI 원아 행동 패턴 분석

### 원장
- 신규 가입 시 무조건 'PENDING' 상태를 부여하며 원장의 승인을 통해서만 해당 역할(교사/학부모)에 맞는 화면으로 라우팅하는 Role-Based Access Control 방법을 사용했습니다.
- 외부 라이브러리 없이 20분 단위 하원 트래픽 밀도를 연산합니다. 그리고 LayoutParams를 직접 동적 제어하는 커스텀 바 차트엔진으로 구성했습니다. 실시간 트래픽 시각화 차트로 관리자가 문제를 즉시 파악 할 수 있습니다.

<br>

## 개발하면서 있었던 기술적 문제와 해결

### 다중 양육자 데이터 충돌 현상
* 문제: 아이의 이름(`황장군`)만으로 DB 문서를 생성할 경우 동명이인 발생 시 데이터가 덮어씌워지거나 엄마 폰과 아빠 폰에서 각기 다른 상태가 노출되는 동기화 붕괴 문제 발생했습니다.
* 해결: Firestore NoSQL의 Document ID를 `[반이름]_[원아이름]` (ex: `새싹1반_김지훈`) 형태의 복합키로 설계
* 양육자(부/모)가 각자의 스마트폰에서 접근하더라도 완벽하게 동일한 Document를 바라보게 만들어 한쪽에서 버튼을 누르면 다른 스마트폰과 교사 대시보드에 딜레이 없이 100% 양방향 동기화가 가능합니다. 

### 대규모 트래픽 렌더링 부하 방어
* 문제: 하원 시간에 트래픽이 몰릴 때마다 DB를 계속 새로 긁어오면 과도한 읽기 비용 및 UI 버벅임이 발생합니다.
* 해결: `addSnapshotListener`를 통해 원본 데이터를 한 번만 싱크하고 이후의 반별 필터링 연산은 캐싱된 `rawDataList`를 활용해 클라이언트 단에서 메모리 연산으로 처리했습니다.

<br>

## 5. Firestore DB Schema
```text
 Firestore Root
 ┣  users (사용자 계정 및 권한 관리)
 ┃ ┗  {User_UID} 
 ┃    ┣  role: "ADMIN" | "TEACHER" | "PARENT"
 ┃    ┗  status: "PENDING" | "APPROVED"
 ┃
 ┣  pickup_status (실시간 하원 상태 동기화)
 ┃ ┗  {class_name}_{student_name} (ex: 새싹1반_김지훈)
 ┃    ┣  status: "WAITING_MORNING" | "ATTENDED" | "APPROACHING" | "ARRIVED"
 ┃    ┗  eta: "약 15분"
 ┃
 ┗  pickup_logs (AI 분석용 누적 로그 적재)
   ┗  {Auto_ID}
      ┣  student_name: "김지훈"
      ┗  duration_seconds: 125 (하원 소요 시간)
