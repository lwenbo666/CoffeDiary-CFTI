pluginManagement {
    repositories {
        // 阿里云镜像 - Google
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // 阿里云镜像 - Maven Central
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 阿里云镜像 - Gradle Plugin
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // 阿里云镜像 - JCenter
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
        // 保留官方仓库作为备用
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像优先
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
        // 保留官方仓库作为备用
        google()
        mavenCentral()
    }
}

rootProject.name = "CoffeeDiary"
include(":app")
