plugins {
    alias(libs.plugins.geet.android.library)
    alias(libs.plugins.geet.android.compose)
    alias(libs.plugins.geet.screenshot)
}

android { namespace = "dev.sumdahl.geet.designsystem" }

dependencies {
    api(libs.coil.compose)
    api(libs.material.kolor)
}
