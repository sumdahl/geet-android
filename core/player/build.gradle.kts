plugins {
    alias(libs.plugins.geet.android.library)
    alias(libs.plugins.geet.hilt)
}

android { namespace = "dev.sumdahl.geet.player" }

dependencies {
    api(projects.core.data)
    api(libs.media3.exoplayer)
    api(libs.media3.session)
}
