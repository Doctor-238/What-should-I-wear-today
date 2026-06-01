# 오늘 뭐 입지?

> 내 옷장에서 AI가 날씨에 맞는 코디를 추천해주는 Android 앱

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue)
![AI](https://img.shields.io/badge/AI-Google%20Gemini-4285F4?logo=google&logoColor=white)

<br>

## 데모

<video src="docs/assets/demo_hq.mp4" autoplay loop muted playsinline width="720"></video>

<br>

## 화면 구성

<table>
  <tr>
    <td align="center"><img src="docs/assets/screen_home.png" width="160"><br><sub><b>홈 · AI 코디 추천</b></sub></td>
    <td align="center"><img src="docs/assets/screen_closet.png" width="160"><br><sub><b>옷장 목록</b></sub></td>
    <td align="center"><img src="docs/assets/screen_add_clothing.png" width="160"><br><sub><b>옷 추가 · AI 분석</b></sub></td>
    <td align="center"><img src="docs/assets/screen_style_list.png" width="160"><br><sub><b>스타일 목록</b></sub></td>
    <td align="center"><img src="docs/assets/screen_style_create.png" width="160"><br><sub><b>나만의 스타일</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/assets/screen_settings_main.png" width="160"><br><sub><b>설정</b></sub></td>
    <td align="center"><img src="docs/assets/screen_settings_detail.png" width="160"><br><sub><b>체형 · 온도 설정</b></sub></td>
    <td align="center"><img src="docs/assets/screen_settings_info.png" width="160"><br><sub><b>사용자 정보</b></sub></td>
    <td align="center"><img src="docs/assets/screen_shopping_brands.png" width="160"><br><sub><b>쇼핑몰 선택</b></sub></td>
    <td align="center"><img src="docs/assets/screen_shopping_list.png" width="160"><br><sub><b>오늘 뭐 살래?</b></sub></td>
  </tr>
</table>

<br>

## 기술 구조

<img src="docs/assets/architecture.png" width="700">

<br>

## 주요 기능

### AI 코디 추천

현재 위치를 기반으로 날씨 데이터를 가져와 Gemini AI가 내 옷장 안의 옷들로 코디를 구성합니다.

- **날짜 선택** — 오늘 / 내일 / 3일 뒤까지 날짜별 코디 미리 확인 (설정에서 확장 예보 활성화 시 최대 5일)
- **최적 코디** — AI가 날씨에 가장 잘 맞는 상의·하의·아우터 조합을 최적 코디로 선정
- **카테고리별 추천** — 최적 코디 외 추천 상의·하의·아우터 목록을 별도 제공
- **일교차 알림** — 하루 최고·최저 기온 차가 12°C 이상이면 챙겨갈 아우터를 별도 추천 (특별 아이콘 표시)
- **우산 알림** — 강수확률 40% 이상 / 70% 이상 시 단계별 배너 메시지 자동 표시
- **체질·온도 반영** — 체질 설정(추위/더위 민감도)과 온도 범위(좁게/보통/넓게)가 추천 로직에 직접 반영
- **착용 목적** — 격식 / 일상 / 활동 / 데이트 / 집앞 등 목적을 설정하면 상황에 맞는 코디 추천

---

### 옷장 관리

| 기능 | 설명 |
|---|---|
| AI 이미지 분석 | 사진 업로드 시 Gemini가 카테고리(상의·하의·아우터 등), 적정 온도 범위, 색상을 자동 추출 |
| 배경 제거 | ML Kit Selfie Segmentation으로 옷 배경 제거, 민감도 1~5단계 조절 가능 |
| 일괄 추가 | 여러 장 동시 선택 → WorkManager로 백그라운드 처리, 앱 종료 후에도 계속 진행 |
| 검색 · 정렬 | 이름 검색, 최신순 정렬 지원 |
| 카테고리 탭 | 전체 / 상의 / 하의 / 아우터 / 진발 탭 전환 |
| 편집 · 삭제 | 등록된 옷의 정보 수정 및 개별 삭제 |

---

### 스타일 저장

- 홈 화면에서 AI 추천 코디를 한 번에 스타일로 저장
- 원하는 옷을 직접 골라 나만의 스타일 조합 생성
- **계절별 분류** — 봄 / 여름 / 가을 / 겨울
- 스타일 이름 설정 및 편집, 스타일 내 옷 삭제 시 해당 스타일 자동 정리

---

### 쇼핑 연동

- **외부 쇼핑몰** — 무신사, 하이버, 네이버스토어, 에이블리, 29CM, 구구, 구글 쇼핑 등 주요 플랫폼 바로 연결
- **AI 추천 기반 검색** — 오늘의 AI 코디 추천 결과를 바탕으로 아이템 검색 자동 연동
- **내부 쇼핑몰 (`오늘 뭐 살래?`)** — 앱 내 상품 목록, 카테고리 필터(상의·하의·아우터·신발·기타), 검색
- **찜 (위시리스트)** — 상품 하트 버튼으로 찜 저장 및 위시리스트 관리

---

### 맞춤 설정

| 설정 | 옵션 |
|---|---|
| 체질 | 5단계 (추위 많이 탐 → 더위 많이 탐) |
| 온도 범위 | 좁게 / 보통 / 넓게 |
| AI 모델 | 빠름 (Gemini 2.5 Flash Lite) / 정확 (Gemini 2.5 Flash) |
| 체형 정보 | 키·몸무게·허리 등록 → 추천 아이콘에 사이즈 표시 |
| 사이즈 표기 | 영문 (S/M/L/XL) / 숫자 (상의 85~110, 하의 26~34) |
| 착용 목적 | 격식 / 일상 / 활동 / 데이트 / 집앞 + 커스텀 목적 추가 |
| 추천 아이콘 표시 | 옷장에서 오늘 추천 여부 아이콘 on/off |
| 배경 제거 민감도 | 1~5단계 |
| 확장 예보 | on 시 3일 뒤 이후 날짜 코디 확인 가능 |
| 전체 초기화 | 모든 설정 기본값으로 리셋 |

---

### 홈 위젯

홈 화면에서 앱을 열지 않고 오늘의 추천 코디를 바로 확인할 수 있는 위젯을 지원합니다.

<br>

## 아키텍처

**패턴:** MVVM + Repository + Single Activity

```
UI Layer
  Fragment (HomeFragment, ClosetFragment, StyleFragment, SettingsFragment, ...)
  ↓ LiveData 관찰 / Event 구독
ViewModel Layer
  HomeViewModel, ClosetViewModel, StyleViewModel, MallMainViewModel, ...
  ↓ suspend 함수 / Coroutines
Repository Layer
  ClothingRepository  ─── Room DB (ClothingItem, v13)
  StyleRepository     ─── Room DB (SavedStyle, StyleItemCrossRef)
  WeatherRepository   ─── OpenWeatherMap REST API (Retrofit2)
  MallRepository      ─── Room DB (MallItem, MallDatabase)
  ↓ 외부 API 직접 호출
External Services
  Google Gemini API   ── 옷 이미지 분석 + 코디 추천 (Flash / Flash Lite)
  ML Kit              ── 옷 배경 제거 (Selfie Segmentation)
  Google Play Location── 현재 위치 좌표 취득
```

**Navigation:** `MainActivity` 단일 Activity + Navigation Component, 하단 탭 4개 (홈·옷장·스타일·설정)  
**비동기:** `viewModelScope.launch` 기반 Kotlin Coroutines 전체 적용  
**이미지 로딩:** Glide + `MyAppGlideModule`  
**백그라운드 작업:** WorkManager (`BatchAddWorker`, `WidgetUpdateWorker`, `MallBatchAddWorker`)

<br>

## 개발 환경

| 항목 | 내용 |
|---|---|
| 언어 | Kotlin |
| IDE | Android Studio |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 34 |
| 빌드 시스템 | Gradle Kotlin DSL |
| UI | XML View System + ViewBinding |
| 아키텍처 | MVVM + Repository |
| 비동기 | Kotlin Coroutines |
| 로컬 DB | Room 2.6 (SQLite) |
| 네트워크 | Retrofit2 + OkHttp + kotlinx.serialization |
| AI | Google Gemini 2.5 Flash / Flash Lite |
| 이미지 처리 | ML Kit Selfie Segmentation, Glide 4.16 |
| 백그라운드 | WorkManager 2.9 |
| 위치 | Google Play Services Location 21.3 |

<br>

## 빌드

`local.properties`에 API 키를 먼저 설정합니다.

```properties
WEATHER_API_KEY=your_openweathermap_api_key
GEMINI_API_KEY=your_gemini_api_key
```

```bash
./gradlew assembleDebug          # 디버그 APK 빌드
./gradlew assembleRelease        # 릴리즈 APK 빌드
./gradlew testDebugUnitTest      # 유닛 테스트
./gradlew connectedAndroidTest   # 기기 연결 후 통합 테스트
./gradlew lint                   # 린트 검사
./gradlew clean                  # 빌드 캐시 정리
```

<br>

## 팀

| 이름 | GitHub | 역할 |
|---|---|---|
| 유예현 | [Doctor-238](https://github.com/Doctor-238/) | 기획 · 기능 설계 · Android 개발 · API 연동 |
| 이지원 | [CH4ER1](https://github.com/CH4ER1) | UI/UX 디자인 · 문서 작성 |
