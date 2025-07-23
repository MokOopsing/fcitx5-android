plugins {
    id("org.fcitx.fcitx5.android.lib-convention")
    id("org.fcitx.fcitx5.android.native-app-convention")
    id("org.fcitx.fcitx5.android.build-metadata")
    id("org.fcitx.fcitx5.android.data-descriptor")
    id("org.fcitx.fcitx5.android.fcitx-component")
}

android {
    namespace = "org.fcitx.fcitx5.android.lib.rime"

    defaultConfig {
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                targets("rime")
            }
        }
    }
}

fcitxComponent {
    installPrebuiltAssets = true
}

generateDataDescriptor {
    symlinks.put("usr/share/rime-data/opencc", "usr/share/opencc")
}

dependencies {
    implementation(project(":lib:fcitx5"))
    implementation(project(":lib:plugin-base"))
}
