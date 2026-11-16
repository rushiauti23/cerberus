import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The vault logic: file format, encryption, generator, tag policies.
// Plain Kotlin with no Android code, so its tests run on any machine.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Argon2id. Only Bouncy Castle's own classes are used, not Android's built-in copy.
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("passed", "failed", "skipped")
    }
}
