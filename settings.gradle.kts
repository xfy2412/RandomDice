pluginManagement {
    repositories {
        // 国内镜像放最前：不开代理也能下（镜像缺件时自动回落到下面的官方源）。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")

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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 同上：镜像优先，官方兜底
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")

        google()
        mavenCentral()
    }
}

rootProject.name = "随机骰子"
include(":app")
