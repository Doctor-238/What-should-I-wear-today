## 👕 나만의 코디 추천 앱 **오늘 뭐 입을까?**

>옷을 업로드하면 인공지능이 날씨에 맞게 코디를 추천해요

<a href="https://ibb.co/1GMH9722"><img src="https://i.ibb.co/ZzSyhGTT/image.png" alt="image" border="0"></a>


## 🗒️ 프로젝트 설명  
**오늘 뭐 입지?** 는 날씨에 따라 인공지능이 코디 스타일을 추천하는 앱입니다.

내가 가진 옷, 혹은 가지고 싶은 옷 사진을 업로드하여 스타일을 추천받을 준비를 해주세요

직접 옷을 선택하여 나만의 스타일을 저장할 수 있습니다

## 📱 실행 데모

<table>
  <tr>
    <td><a href="https://ibb.co/vCGmbfcb"><img src="https://i.ibb.co/0pSZk1nk/1-2.png" width="300"></a></td>
    <td><a href="https://ibb.co/SFhQX77"><img src="https://i.ibb.co/1FDsfJJ/2-1.png" width="300"></a></td>
  </tr>
  <tr>
    <td><a href="https://ibb.co/JjCjw3Xb"><img src="https://i.ibb.co/pjnjBhCs/3-1.png" width="300"></a></td>
    <td><a href="https://ibb.co/nMZyqX0p"><img src="https://i.ibb.co/Jj0TWYdZ/4-1.png" width="300"></a></td>
  </tr>
</table>


## 🛠 주요 기능   

### 1. AI 기반 코디 추천 및 개인화
- **현재 및 미래 날씨 반영:** 현재 시간을 기준으로 오늘의 날씨를 반영하며, 내일 날씨까지 고려하여 옷을 추천합니다.

- **온도 기반 추천:** 자체 AI 분석 로직에 따라 상의, 하의, 아우터 등 날씨에 맞는 옷을 추천하며, 가장 적합한 조합은 최적의 코디로 선정하여 제시합니다. 사용자의 체질과 선호도에 맞춰 적정 온도 범위를 조절할 수 있으며, 이 설정은 코디 추천 로직에 직접 반영됩니다.

- **일교차 알림:** 하루 최고/최저 기온차가 12도 이상일 경우, "일교차가 커요. 가벼운 아우터를 챙기세요!"와 같은 메시지와 함께 챙겨갈 아우터를 별도로 추천합니다. 이 아우터는 특별 아이콘으로 구별되어 표시됩니다.

### 2. 스마트한 옷 관리

- **AI 사진 분석:** 옷 사진을 업로드하면 AI가 의류 여부를 판별하고, 자동으로 분류(상의, 하의, 아우터 등), 적정 온도, 색상을 분석합니다.

- **배경 제거:** 업로드된 옷 사진의 배경을 제거할 수 있으며, 배경 제거 민감도를 사용자가 직접 조절할 수 있습니다.

- **일괄 추가:** 여러 옷을 한 번에 추가할 수 있는 기능으로, 백그라운드에서 작업이 진행되며 진행 상황을 알림으로 확인할 수 있습니다. 작업 도중 앱을 종료해도 계속 진행됩니다.

### 3. 사용자 맞춤형 편의 기능

- **스타일 저장 및 관리:** 홈 화면에서 추천된 코디를 스타일로 저장하거나, 여러 옷을 조합하여 자신만의 스타일을 만들 수 있습니다.

- **상황별 알림:** 비 올 확률이 40% 또는 70% 이상일 경우, 우산을 챙기도록 화면 하단에 알림 메시지를 표시합니다.

- **데이터 관리:** 앱 내의 옷과 스타일을 분류별로 관리할 수 있으며, 검색 및 정렬 기능을 지원합니다. 스타일 내 옷을 삭제하면 해당 스타일도 자동으로 삭제되어 불필요한 데이터가 남지 않습니다.

## 💻 개발 및 빌드

### 라이브러리 설치
``` bash
npm install
``` 

### 개발 모드 실행
``` bash
npm run dev
``` 

### 프로덕션 빌드
``` bash
npm run build
``` 

## ⚙️ 개발 환경

언어: Kotlin

IDE: Android Studio

Build Tool: Gradle

UI Framework: XML-based View System

AI 분석: Google Gemini API

Database: Room (SQLite 기반 로컬 DB)

## 📂 프로젝트 구조

```txt
.
├── app
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src
│       ├── androidTest
│       │   └── java
│       │       └── com
│       │           └── yehyun
│       │               └── whatshouldiweartoday
│       ├── main
│       │   ├── AndroidManifest.xml
│       │   ├── ic_launcher-playstore.png
│       │   ├── java
│       │   │   └── com
│       │   │       └── yehyun
│       │   │           └── whatshouldiweartoday
│       │   └── res
│       │       ├── color
│       │       ├── drawable
│       │       ├── layout
│       │       ├── menu
│       │       ├── mipmap-anydpi-v26
│       │       ├── mipmap-hdpi
│       │       ├── mipmap-mdpi
│       │       ├── mipmap-xhdpi
│       │       ├── mipmap-xxhdpi
│       │       ├── mipmap-xxxhdpi
│       │       ├── navigation
│       │       ├── values
│       │       └── xml
│       └── test
│           └── java
│               └── com
│                   └── yehyun
│                       └── whatshouldiweartoday
├── build.gradle.kts
├── gradle
│   └── wrapper
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradle.properties
├── gradlew
├── gradlew.bat
├── local.properties
├── project-structure-detailed.txt
├── project-structure.txt
├── project-structure1.txt
└── settings.gradle.kts
```


## 🧑‍💻 역할 분담
- 2171297 유예현 [(Doctor-238)](https://github.com/Doctor-238/)
  - 기획 / 기능 설계
  - 프론트엔드 개발
  - 백엔드 연동, 로컬 데이터 처리

- 2171429 이지원 [(CH4ER1)](https://github.com/CH4ER1)
  - UI/UX 디자인
  - 문서 작성

## ✍️ 디자인 원본 (Canva)
- https://www.canva.com/design/DAGrymWtjvE/3CGKh1Iugw0eGi7S_W8V1A/edit?utm_content=DAGrymWtjvE&utm_campaign=designshare&utm_medium=link2&utm_source=sharebutton
