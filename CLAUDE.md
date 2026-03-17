# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Run from the project root (`C:\Androidstudio\What-should-I-wear-today`):

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew testDebugUnitTest      # Run unit tests (debug)
./gradlew connectedAndroidTest   # Run instrumented tests (device required)
./gradlew lint                   # Run lint checks
./gradlew clean                  # Clean build outputs
```

## Project Overview

"오늘 뭐 입지?" (What Should I Wear Today?) — Android app that recommends outfits based on current/forecast weather using AI.

- **Min SDK:** 26 (Android 8.0) | **Target/Compile SDK:** 34
- **Language:** Kotlin | **Build:** Gradle Kotlin DSL
- **Package:** `com.yehyun.whatshouldiweartoday`

## Architecture

**Pattern:** MVVM + Repository + Single Activity

```
UI (Fragments)
  ↓ observes LiveData
ViewModels (AndroidViewModel / ViewModel)
  ↓ calls
Repositories (ClothingRepository, StyleRepository, WeatherRepository)
  ↓
Data Sources:
  - Room DB (local clothing/style data)
  - Retrofit → OpenWeatherMap API (weather)
  - Google Generative AI SDK → Gemini API (clothing analysis & outfit recommendation)
  - ML Kit (background removal from clothing photos)
  - SharedPreferences via SettingsManager
```

**Navigation:** Single `MainActivity` hosts all fragments via Navigation Component (`nav_graph.xml`). Bottom navigation with 4 tabs: Home, Closet, Style, Settings.

## Key Packages

| Package | Purpose |
|---|---|
| `ui/home/` | Weather fetch, AI outfit recommendation, ViewPager (today/tomorrow) |
| `ui/closet/` | Wardrobe CRUD, AI image analysis, batch upload via WorkManager |
| `ui/style/` | Save/edit outfit combinations, organized by season |
| `ui/settings/` | User preferences (constitution level, temperature tolerance, AI model) |
| `data/api/` | Retrofit service + response models for OpenWeatherMap |
| `data/database/` | Room entities (`ClothingItem`, `SavedStyle`), DAOs, cross-ref table |
| `data/preference/` | `SettingsManager` — SharedPreferences wrapper |
| `data/repository/` | Repository classes (single source of truth) |
| `ai/` | `AiModelProvider` — Gemini model initialization |

## Room Database

**Version 13** — migrations required when modifying schema.

- `ClothingItem`: stores clothing metadata including `suitableTemperature`, `baseTemperature`, `category`, `colorHex`, and image URIs (original + background-removed)
- `SavedStyle`: named outfit collections with `season` field
- `StyleItemCrossRef`: M2M junction table linking styles to clothing items

## Core Feature: AI Recommendation Flow

1. `HomeViewModel` fetches device location via Google Play Services Location
2. Calls OpenWeatherMap API for current + tomorrow's forecast
3. Loads all `ClothingItem` records from Room DB
4. Sends clothing + weather context to Gemini API
5. Gemini returns structured JSON with recommended outfit combinations
6. Results displayed in `RecommendationFragment` via `RecommendationAdapter`

Special cases: rain probability ≥40% triggers umbrella notification; temperature delta ≥12° triggers packable outer recommendation.

## Key Technical Details

- **Async:** Kotlin Coroutines throughout; `viewModelScope.launch` in ViewModels
- **Image loading:** Glide with custom `MyAppGlideModule`
- **Batch clothing add:** `BatchAddWorker` (WorkManager) for background processing
- **Event wrapper:** `util/Event.kt` — single-consumption LiveData events used in ViewModels
- **Tab reselect:** `OnTabReselectedListener` interface scrolls list to top on tab re-tap
- **Widget:** `TodayRecoWidgetProvider` + `WidgetConfigurationActivity` for home screen widget
- **AI model choice:** Users can toggle between Gemini Flash (fast) and Gemini Pro (accurate) in settings

## External APIs

- **OpenWeatherMap** — weather forecast (API key stored in `local.properties` or BuildConfig)
- **Google Gemini** — clothing image analysis + outfit recommendation (API key in BuildConfig)
- **ML Kit Selfie Segmentation** — background removal from clothing photos
