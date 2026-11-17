// Wake Plan — settings
// 国内镜像（用户偏好）：阿里云 maven 三件套放最前，官方源兜底。
// 镜像只对国内网络友好，CI 跑在境外 runner（GitHub Azure）上时用
// -PchinaMirror=false 关掉，只走官方源——否则插件解析会整体失败。

pluginManagement {
    val chinaMirror = (gradle.startParameter.projectProperties["chinaMirror"] ?: "true").toBoolean()
    repositories {
        if (chinaMirror) {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val chinaMirror = (gradle.startParameter.projectProperties["chinaMirror"] ?: "true").toBoolean()
    repositories {
        if (chinaMirror) {
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "WakePlan"

include(":app")
