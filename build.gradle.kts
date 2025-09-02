plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.application)
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotest)
}

group = "dev.babies"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.clikt)

    implementation(libs.docker.java.core)
    implementation(libs.docker.java.transport.zerodep)

    implementation(libs.kaml)
    implementation(libs.koin.core)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlincrypto.hash.sha1)

    implementation(libs.log4j.slf4j.impl)

    implementation(libs.mongo.driver.coroutines)
    implementation(libs.postgres)

    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}

application {
    mainClass.set("dev.babies.MainKt")
}

tasks {
    shadowJar {
        archiveBaseName.set("app")
        manifest {
            attributes["Main-Class"] = application.mainClass
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}