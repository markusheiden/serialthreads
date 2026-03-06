plugins {
    `java-library`
    `java-test-fixtures`
    `maven-publish`
    alias(libs.plugins.versions)
}

group = "org.serialthreads"

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
    testImplementation(testFixtures(project(":")))

    testFixturesImplementation(platform(libs.spring.boot.bom))
    testFixturesImplementation(libs.junit.platform.launcher)
    testFixturesImplementation(libs.slf4j.api)
    testFixturesImplementation(libs.asm)
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
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
