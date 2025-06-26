pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // ML Kit 라이브러리를 다운로드하기 위해 google() 저장소가 필수입니다.
        google()
        mavenCentral()
    }
}
rootProject.name = "What-should-I-wear-today"
include(":app")

 