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

### 1. 온도 기반 스타일 추천
- 사용자의 체질에 따라 체감 온도를 조절할 수 있어 더 정확한 스타일 추천 가능
- 현재 날씨와 체감 온도를 반영해 계절 및 날씨에 적합한 스타일 추천


### 2. 옷 업로드 및 스타일 분석
- 단건 또는 다건의 옷 사진 업로드 가능
- 업로드된 사진을 AI가 자동 분석하여 옷의 카테고리(상의, 하의, 아우터 등), 적정 착용 온도, 색상 등을 자동으로 분류
- 계절별로 스타일 저장이 가능하여 봄, 여름, 가을, 겨울에 맞는 스타일 정리 지원
 
### 3. 기타 기능
- 옷 사진 업로드 시 배경 제거 (따로 민감도 설정 가능)
- 일교차가 큰 날일 경우에는 아우터 따로 챙겨가는 것을 권장하는 문구 추가 
- 오늘 날씨 뿐만 아니라 내일 날씨도 추가하여 코디 추천

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
<br>
- 2171429 이지원 [(CH4ER1)](https://github.com/CH4ER1)
  - UI/UX 디자인
  - 문서 작성

## ✍️ 디자인 원본 (Canva)
- https://www.canva.com/design/DAGrymWtjvE/3CGKh1Iugw0eGi7S_W8V1A/edit?utm_content=DAGrymWtjvE&utm_campaign=designshare&utm_medium=link2&utm_source=sharebutton
