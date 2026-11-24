plugins {
    java
    jacoco
    checkstyle
    id("org.springframework.boot") version "4.1.2"
    id("com.diffplug.spotless") version "8.10.4"
    id("com.github.spotbugs") version "6.5.13"
}

group = "org.courseflow"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    implementation("org.springframework.boot:spring-boot-starter-mail")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testcontainers
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    // SSH connection to remote Docker
    testImplementation("com.github.mwiede:jsch:2.28.4")
}

// Retain names of controller parameters for Spring MVC and OpenAPI.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required = true
        xml.required = true
    }
}

checkstyle {
    toolVersion = "12.3.1"
    configProperties["org.checkstyle.google.severity"] = "warning"
    maxWarnings = Int.MAX_VALUE
}

tasks.withType<org.gradle.api.plugins.quality.Checkstyle>().configureEach {
    reports {
        html.required = true
        xml.required = true
    }
}

spotless {
    java {
        googleJavaFormat("1.28.0")
    }
}

// Only documented Spring DI and Hibernate-managed state are excluded.
// Other SpotBugs findings still fail verification.
spotbugs {
    excludeFilter = rootProject.file("config/spotbugs/exclude.xml")
}