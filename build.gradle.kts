plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.versions)
}

tasks.wrapper {
    gradleVersion = libs.versions.gradle.get()
}

group = "org.serialthreads"
version = "1.0-SNAPSHOT"
base {
    archivesName = "serialhtreads"
}

repositories {
    mavenCentral()
}

java {
    // https://docs.gradle.org/current/userguide/toolchains.html
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        // Use Eclipse Temurin (provided by Adoptium).
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

dependencies {
    implementation(platform(libs.spring.boot.bom))

    implementation(libs.slf4j.api)

    implementation(libs.asm)
    implementation(libs.asm.analysis)
    implementation(libs.asm.tree)
    implementation(libs.asm.util)

    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to "SerialThreads",
                "Implementation-Version" to archiveVersion.get(),
                "Premain-Class" to "org.serialthreads.agent.Agent",
                "Agent-Class" to "org.serialthreads.agent.Agent",
                "Can-Redefine-Classes" to false,
                "Can-Retransform-Classes" to false
            )
        )
    }
}

val testJar by tasks.registering(Jar::class) {
    archiveClassifier = "tests"
    from(sourceSets.test.get().output)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
        create<MavenPublication>("mavenTests") {
            artifact(testJar)
        }
    }
}
tasks.build {
    dependsOn(tasks.publishToMavenLocal)
}

tasks.test {
    useJUnitPlatform()

    // ignore failing tests
    ignoreFailures = true
}

sourceSets {
    val main by getting
    val test by getting
    create("performanceTest") {
        java {
            compileClasspath += main.output + test.output
            runtimeClasspath += main.output + test.output
            srcDir(file("src/test/performance/java"))
        }
    }
}
