import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.spring.boot)
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// A stable artefact name keeps the Dockerfile from hard-coding a version number.
tasks.named<BootJar>("bootJar") {
    archiveFileName = "cyanotypes.jar"
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":imaging"))
    implementation(libs.spring.boot.starter.web)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}
