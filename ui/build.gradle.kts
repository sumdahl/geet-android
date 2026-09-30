plugins {
    alias(libs.plugins.geet.android.library)
    alias(libs.plugins.geet.android.compose)
    alias(libs.plugins.geet.hilt)
    alias(libs.plugins.geet.screenshot)
}

android { namespace = "dev.sumdahl.geet.ui" }

dependencies {
    api(projects.core.data)
    api(projects.core.player)
    api(projects.core.designsystem)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.compose.material3.adaptive.suite)
    implementation(libs.reorderable)
}
