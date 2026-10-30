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
        google()
        mavenCentral()
    }
}
rootProject.name = "Beeze"
include(":app")

// Folia 歌词 SDK：作为源码模块接入（位于项目同级目录）
include(":folialyric")
project(":folialyric").projectDir = File(rootDir, "../folia-lyric-sdk")
