
plugins {
    java
    id("org.springframework.boot") version "4.1.2"
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
}

// Retain names of controller parameters for Spring MVC and OpenAPI.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}
