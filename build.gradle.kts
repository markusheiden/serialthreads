import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    `java-test-fixtures`
    `maven-publish`
    jacoco
    alias(libs.plugins.error.prone)
    alias(libs.plugins.versions)
}

tasks.wrapper {
    gradleVersion = libs.versions.gradle.get()
}

repositories {
    mavenCentral()
}

java {
    // https://docs.gradle.org/current/userguide/toolchains.html
    toolchain {
        languageVersion = JavaLanguageVersion.of(27)
        // Use Eclipse Temurin (provided by Adoptium).
        vendor = JvmVendorSpec.ADOPTIUM
    }

    withSourcesJar()
    withJavadocJar()
}

configurations.all {
    resolutionStrategy.failOnDynamicVersions()
}

dependencies {
    implementation(platform(libs.spring.boot.bom))

    api(libs.jspecify)
    implementation("org.slf4j:slf4j-api")

    implementation(libs.asm)
    implementation(libs.asm.analysis)
    implementation(libs.asm.tree)
    implementation(libs.asm.util)

    implementation(libs.requirements)

    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("ch.qos.logback:logback-classic")
    testImplementation(testFixtures(project(":")))

    errorprone(libs.error.prone)
    errorprone(libs.nullaway)

    testFixturesImplementation(platform(libs.spring.boot.bom))
    testFixturesImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        // Check just the null safety of the production code.
        disableAllChecks = true
        enabled = !name.contains("test", ignoreCase = true)
        error("NullAway")
        option("NullAway:AnnotatedPackages", "org.serialthreads")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.withType<AbstractArchiveTask> {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
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
    // dependsOn(tasks.publishToMavenLocal)
}

tasks.test {
    useJUnitPlatform()

    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

sourceSets {
    create("performanceTest") {
        java {
            srcDir(file("src/test/performance"))
        }
    }
}

configurations {
    getByName("performanceTestImplementation").extendsFrom(configurations.testImplementation.get())
    getByName("performanceTestRuntimeOnly").extendsFrom(configurations.testRuntimeOnly.get())
}
