plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // TwelveMonkeys widens ImageIO: 16-bit PNG, CMYK JPEG, TIFF, better metadata.
    // It registers itself through the ImageIO SPI, no wiring needed.
    implementation(libs.twelvemonkeys.jpeg)
    implementation(libs.twelvemonkeys.tiff)

    // PDF output. Pure Java, no native code.
    implementation(libs.pdfbox)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}
