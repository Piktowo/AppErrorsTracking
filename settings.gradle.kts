pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://repo.xposed.info/")
        maven("https://raw.githubusercontent.com/fankes/maven-repository/main/repository/releases")
    }
}
plugins {
    id("com.highcapable.gropify") version "1.0.1"
}
gropify {
    global {
        common {
            permanentKeyValues(
                "GITHUB_CI_COMMIT_ID" to ""
            )
            includeKeys(
                "GITHUB_CI_COMMIT_ID",

                "^project\\..*\$".toRegex()
            )
            locations(GropifyLocation.RootProject, GropifyLocation.SystemEnv)
        }
        android {
            existsPropertyFiles(".secret/secret.properties")
            includeKeys("GITHUB_CI_COMMIT_ID")
            // 手动指定类型，防止一些特殊 "COMMIT ID" 被生成为数值
            keyValuesRules("GITHUB_CI_COMMIT_ID" to ValueRule(String::class))
        }
    }
    rootProject { common { isEnabled = false } }
}
rootProject.name = "AppErrorsTracking"
include(":module-app", ":demo-app")