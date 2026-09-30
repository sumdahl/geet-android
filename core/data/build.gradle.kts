plugins {
    alias(libs.plugins.geet.android.library)
    alias(libs.plugins.geet.hilt)
}

android { namespace = "dev.sumdahl.geet.data" }

dependencies {
    api(projects.core.engine)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    api(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
}
