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
        // DexKit 发布在 jitpack
        maven("https://jitpack.io")
        // Xposed API 的 jar 当年发布在 jcenter，jcenter 关站后 Maven Central 上没有，
        // 只能从这个专门维护的镜像拉。
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "GboardHooker"
include(":app")
