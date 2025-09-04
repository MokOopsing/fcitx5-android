plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.fcitx.fcitx5.android.plugin.rime"

    defaultConfig {
        minSdk = 23
        // targetSdk 不再直接写在 library
    }

    buildTypes {
        release {
            isMinifyEnabled = false // library 里正确写法
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            targets("rime")
        }
    }

    // lint 或 testOptions 可以设置 targetSdk
    lint {
        textReport = true
        lintConfig = file("$projectDir/lint.xml")
    }
}

dependencies {
    implementation(project(":lib:plugin-base"))
}
