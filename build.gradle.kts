plugins {
    `java-library`
    `maven-publish`
    signing
    jacoco
}

description = "Drives SLF4J/Logback log levels from LaunchDarkly feature flags"

java {
    // Pin the toolchain so the build is independent of whatever JDK is on PATH.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

// A second test source set that runs with no SLF4J backend on the classpath.
// The main test run always has Logback bound, so it cannot reach the degraded
// path that a consumer with no backend hits.
val noBackendTest: SourceSet = sourceSets.create("noBackendTest") {
    compileClasspath += sourceSets["main"].output + sourceSets["test"].output
    runtimeClasspath += output + compileClasspath
}

// One test source set per alternative backend. Each binds SLF4J to a different
// implementation, which is the only way to prove detection picks the right
// bridge - a single classpath can only ever have one backend bound.
val log4j2BackendTest: SourceSet = sourceSets.create("log4j2BackendTest") {
    compileClasspath += sourceSets["main"].output + sourceSets["test"].output
    runtimeClasspath += output + compileClasspath
}

val julBackendTest: SourceSet = sourceSets.create("julBackendTest") {
    compileClasspath += sourceSets["main"].output + sourceSets["test"].output
    runtimeClasspath += output + compileClasspath
}

// A third test source set for the case where Logback is on the classpath but
// SLF4J is bound to a different backend, so mutating Logback's context would
// have no effect on what the application actually logs through.
val mismatchedBackendTest: SourceSet = sourceSets.create("mismatchedBackendTest") {
    compileClasspath += sourceSets["main"].output + sourceSets["test"].output
    runtimeClasspath += output + compileClasspath
}

val launchDarklySdkVersion = "7.16.0"
val logbackVersion = "1.6.3"
val slf4jVersion = "2.0.19"
val log4jVersion = "2.26.1"
val junitVersion = "6.1.3"
val assertjVersion = "3.27.7"

dependencies {
    // SLF4J's Level type appears in this library's public API.
    api("org.slf4j:slf4j-api:$slf4jVersion")

    // Consumer-supplied, like a peerDependency: we never want to force an SDK
    // version on an application that already depends on one.
    compileOnly("com.launchdarkly:launchdarkly-java-server-sdk:$launchDarklySdkVersion")

    // The logging backend is the application's choice; we only bind to whichever
    // one is present. java.util.logging needs no dependency, being part of the JDK.
    compileOnly("ch.qos.logback:logback-classic:$logbackVersion")
    compileOnly("org.apache.logging.log4j:log4j-core:$log4jVersion")

    testImplementation("com.launchdarkly:launchdarkly-java-server-sdk:$launchDarklySdkVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Deliberately no logging backend here: this source set covers what happens
    // when the application never binds one.
    "noBackendTestImplementation"("org.slf4j:slf4j-api:$slf4jVersion")
    "noBackendTestImplementation"(
        "com.launchdarkly:launchdarkly-java-server-sdk:$launchDarklySdkVersion")
    "noBackendTestImplementation"(platform("org.junit:junit-bom:$junitVersion"))
    "noBackendTestImplementation"("org.junit.jupiter:junit-jupiter")
    "noBackendTestImplementation"("org.assertj:assertj-core:$assertjVersion")
    "noBackendTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")

    // Both Logback and Log4j 2 are present here, but the test task forces SLF4J
    // to bind elsewhere - the common case where a dependency drags in a backend
    // the application does not actually log through.
    "mismatchedBackendTestImplementation"("org.slf4j:slf4j-api:$slf4jVersion")
    "mismatchedBackendTestImplementation"("ch.qos.logback:logback-classic:$logbackVersion")
    "mismatchedBackendTestImplementation"("org.apache.logging.log4j:log4j-core:$log4jVersion")
    "mismatchedBackendTestImplementation"(
        "com.launchdarkly:launchdarkly-java-server-sdk:$launchDarklySdkVersion")
    "mismatchedBackendTestImplementation"(platform("org.junit:junit-bom:$junitVersion"))
    "mismatchedBackendTestImplementation"("org.junit.jupiter:junit-jupiter")
    "mismatchedBackendTestImplementation"("org.assertj:assertj-core:$assertjVersion")
    "mismatchedBackendTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")

    // Log4j 2 as the bound backend, via its SLF4J binding.
    "log4j2BackendTestImplementation"("org.slf4j:slf4j-api:$slf4jVersion")
    "log4j2BackendTestImplementation"("org.apache.logging.log4j:log4j-core:$log4jVersion")
    "log4j2BackendTestRuntimeOnly"(
        "org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")
    "log4j2BackendTestImplementation"(
        "com.launchdarkly:launchdarkly-java-server-sdk:$launchDarklySdkVersion")
    "log4j2BackendTestImplementation"(platform("org.junit:junit-bom:$junitVersion"))
    "log4j2BackendTestImplementation"("org.junit.jupiter:junit-jupiter")
    "log4j2BackendTestImplementation"("org.assertj:assertj-core:$assertjVersion")
    "log4j2BackendTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")

    // java.util.logging as the bound backend. JUL itself is in the JDK; only the
    // SLF4J binding is a dependency.
    "julBackendTestImplementation"("org.slf4j:slf4j-api:$slf4jVersion")
    "julBackendTestRuntimeOnly"("org.slf4j:slf4j-jdk14:$slf4jVersion")
    "julBackendTestImplementation"(
        "com.launchdarkly:launchdarkly-java-server-sdk:$launchDarklySdkVersion")
    "julBackendTestImplementation"(platform("org.junit:junit-bom:$junitVersion"))
    "julBackendTestImplementation"("org.junit.jupiter:junit-jupiter")
    "julBackendTestImplementation"("org.assertj:assertj-core:$assertjVersion")
    "julBackendTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val noBackendTestTask = tasks.register<Test>("noBackendTest") {
    group = "verification"
    description = "Runs tests with no SLF4J backend on the classpath."
    testClassesDirs = noBackendTest.output.classesDirs
    classpath = noBackendTest.runtimeClasspath
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val mismatchedBackendTestTask = tasks.register<Test>("mismatchedBackendTest") {
    group = "verification"
    description = "Runs tests with Logback present but SLF4J bound to another backend."
    testClassesDirs = mismatchedBackendTest.output.classesDirs
    classpath = mismatchedBackendTest.runtimeClasspath
    // slf4j-api 2.x honours this over service loading, which makes the
    // otherwise non-deterministic "which backend won" situation reproducible.
    systemProperty("slf4j.provider", "org.slf4j.helpers.NOP_FallbackServiceProvider")
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val log4j2BackendTestTask = tasks.register<Test>("log4j2BackendTest") {
    group = "verification"
    description = "Runs tests with SLF4J bound to Log4j 2."
    testClassesDirs = log4j2BackendTest.output.classesDirs
    classpath = log4j2BackendTest.runtimeClasspath
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val julBackendTestTask = tasks.register<Test>("julBackendTest") {
    group = "verification"
    description = "Runs tests with SLF4J bound to java.util.logging."
    testClassesDirs = julBackendTest.output.classesDirs
    classpath = julBackendTest.runtimeClasspath
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.check {
    dependsOn(
        noBackendTestTask,
        mismatchedBackendTestTask,
        log4j2BackendTestTask,
        julBackendTestTask)
}

tasks.jacocoTestReport {
    // Coverage is only meaningful across both test runs, since each covers a
    // path the other cannot reach.
    dependsOn(
        tasks.test,
        noBackendTestTask,
        mismatchedBackendTestTask,
        log4j2BackendTestTask,
        julBackendTestTask)
    executionData(
        tasks.test.get(),
        noBackendTestTask.get(),
        mismatchedBackendTestTask.get(),
        log4j2BackendTestTask.get(),
        julBackendTestTask.get())
    reports {
        xml.required = true
        html.required = true
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = "LaunchDarkly Java Logger"
                description = project.description
                url = "https://github.com/bradbunce/launchdarkly-java-logger"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                developers {
                    developer {
                        id = "bradbunce"
                        name = "Brad Bunce"
                        url = "https://github.com/bradbunce"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/bradbunce/launchdarkly-java-logger.git"
                    developerConnection = "scm:git:ssh://github.com/bradbunce/launchdarkly-java-logger.git"
                    url = "https://github.com/bradbunce/launchdarkly-java-logger"
                }
            }
        }
    }
}

signing {
    // Only sign when credentials are present, so local builds work unsigned.
    val signingKey: String? = System.getenv("SIGNING_KEY")
    val signingPassword: String? = System.getenv("SIGNING_PASSWORD")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}