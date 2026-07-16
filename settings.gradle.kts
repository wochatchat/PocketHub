pluginManagement {
    repositories {
        google { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral { url = uri("https://maven.aliyun.com/repository/central") }
    }
}

rootProject.name = "PocketHub"
include(":app")
