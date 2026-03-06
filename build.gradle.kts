plugins {
    `java-library`
    `maven-publish`
    id("com.github.ben-manes.versions")
    id("io.spring.dependency-management") apply false
}

val slf4jVersion: String by project
val asmVersion: String by project
val junitPlatformVersion: String by project
val junitVersion: String by project
val assertjVersion: String by project
val logbackVersion: String by project

tasks.wrapper {
    gradleVersion = providers.gradleProperty("gradleVersion").get()
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
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-analysis:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")

    testImplementation("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testRuntimeOnly("ch.qos.logback:logback-classic:$logbackVersion")
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
