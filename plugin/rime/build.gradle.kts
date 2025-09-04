plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.fcitx.fcitx5.android.native-app-convention")
}

android {
    namespace = "org.fcitx.fcitx5.android.plugin.rime"

    defaultConfig {
        minSdk = 23
        targetSdk = 34

        externalNativeBuild {
            cmake {
                targets("rime")  // 确保和 CMakeLists.txt 里的 target 对应
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            minifyEnabled = false
        }
    }

    packaging {
        jniLibs {
            excludes += setOf("**/libc++_shared.so")
        }
    }
}

dependencies {
    implementation(project(":lib:plugin-base"))
}
