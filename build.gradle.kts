plugins {
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "dev.cyanotypes"
    version = "0.1.0"
}

subprojects {
    repositories {
        mavenCentral()
    }
}
