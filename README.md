# 오늘 뭐 입지?

> AI가 날씨를 분석해 내 옷장에서 코디를 추천해주는 Android 앱

<br>

## 데모

<video src="docs/assets/demo_hq.mp4" autoplay loop muted playsinline width="720"></video>

<br>

## 화면 구성

<table>
  <tr>
    <td align="center"><img src="docs/assets/screen_home.png" width="160"><br><sub>홈 · 코디 추천</sub></td>
    <td align="center"><img src="docs/assets/screen_closet.png" width="160"><br><sub>옷장</sub></td>
    <td align="center"><img src="docs/assets/screen_add_clothing.png" width="160"><br><sub>옷 추가</sub></td>
    <td align="center"><img src="docs/assets/screen_style_list.png" width="160"><br><sub>스타일 목록</sub></td>
    <td align="center"><img src="docs/assets/screen_style_create.png" width="160"><br><sub>스타일 만들기</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/assets/screen_settings_main.png" width="160"><br><sub>설정</sub></td>
    <td align="center"><img src="docs/assets/screen_settings_detail.png" width="160"><br><sub>체형 · 온도 설정</sub></td>
    <td align="center"><img src="docs/assets/screen_settings_info.png" width="160"><br><sub>사용자 정보</sub></td>
    <td align="center"><img src="docs/assets/screen_shopping_brands.png" width="160"><br><sub>쇼핑몰 선택</sub></td>
    <td align="center"><img src="docs/assets/screen_shopping_list.png" width="160"><br><sub>오늘 뭐 살래?</sub></td>
  </tr>
</table>

<br>

## 기술 구조

<img src="docs/assets/architecture.png" width="720">

<br>

## 주요 기능

### AI 코디 추천
- 현재 위치 기반 실시간 날씨 + 내일 예보를 결합해 Gemini AI가 코디 추천
- 일교차 ≥12°C → 챙겨갈 아우터를 별도 추천 (특별 아이콘 표시)
- 강수확률 ≥40% / ≥70% → 우산 알림 배너 자동 표시
- 오늘 / 내일 탭 전환으로 내일 코디 미리 확인

### 스마트 옷 관리
- **AI 이미지 분석** — 옷 사진 업로드 시 종류·적정온도·색상 자동 추출
- **배경 제거** — ML Kit 기반, 민감도 1~5단계 조절 가능
- **일괄 추가** — 여러 장 선택 후 WorkManager 백그라운드 처리, 앱 종료 후에도 계속 진행
- 카테고리(전체·상의·하의·아우터·진발), 검색, 정렬로 옷장 관리

### 스타일 저장
- 추천된 코디를 한 번에 스타일로 저장
- 계절(봄·여름·가을·겨울)별 분류
- 직접 옷을 골라 나만의 스타일 조합 저장 · 편집

### 쇼핑 연동
- 무신사·하이버·네이버·에이블리·29CM 등 주요 쇼핑몰 바로 연결
- AI 추천 결과 기반 아이템 검색
- 찜(위시리스트) 저장 및 관리

### 맞춤 설정
| 설정 항목 | 옵션 |
|---|---|
| 체질 | 추위 많이 탐 / 조금 탐 / 보통 / 더위 조금 탐 / 많이 탐 (5단계) |
| 적정 온도 범위 | 좁게 / 보통 / 넓게 |
| AI 모델 | 빠름 (Gemini Flash Lite) / 정확 (Gemini Flash) |
| 체형 정보 | 키 · 몸무게 · 허리 등록 → 사이즈 맞춤 아이콘 표시 |
| 사이즈 표기 | 영문(S/M/L) / 숫자(85~110) |
| 착용 목적 | 격식 / 일상 / 활동 / 데이트 / 집앞 + 커스텀 추가 |
| 배경 제거 민감도 | 1~5단계 |

### 홈 위젯
- 오늘의 추천 코디를 홈 화면 위젯으로 확인

<br>

## 아키텍처

**패턴:** MVVM + Repository + Single Activity

```
UI (Fragments)
  ↓ LiveData 관찰
ViewModels (AndroidViewModel / ViewModel)
  ↓ 호출
Repositories (ClothingRepository, StyleRepository, WeatherRepository, MallRepository)
  ↓
Data Sources
  ├── Room DB         — 옷·스타일 로컬 저장 (v13)
  ├── OpenWeatherMap  — 날씨·예보 API
  ├── Google Gemini   — 이미지 분석 + 코디 추천
  └── ML Kit          — 옷 사진 배경 제거
```

**Navigation:** Single `MainActivity` + Navigation Component, 하단 탭 4개 (홈·옷장·스타일·설정)

<br>

## 개발 환경

| 항목 | 내용 |
|---|---|
| 언어 | Kotlin |
| IDE | Android Studio |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 |
| 빌드 | Gradle Kotlin DSL |
| 비동기 | Kotlin Coroutines |
| DB | Room 2.6 (SQLite) |
| AI | Google Gemini 2.5 Flash / Flash Lite |
| 이미지 | ML Kit Selfie Segmentation, Glide |
| 네트워크 | Retrofit2 + OkHttp |
| 백그라운드 | WorkManager |

<br>

## 빌드

```bash
./gradlew assembleDebug          # 디버그 APK 빌드
./gradlew assembleRelease        # 릴리즈 APK 빌드
./gradlew testDebugUnitTest      # 유닛 테스트
./gradlew lint                   # 린트 검사
```

> API 키는 `local.properties`에 `WEATHER_API_KEY` / `GEMINI_API_KEY` 로 설정

<br>

## 팀

| 이름 | 역할 |
|---|---|
| 유예현 [(Doctor-238)](https://github.com/Doctor-238/) | 기획 · 기능 설계 · 프론트엔드 · 백엔드 연동 |
| 이지원 [(CH4ER1)](https://github.com/CH4ER1) | UI/UX 디자인 · 문서 작성 |
