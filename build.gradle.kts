plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("org.openapi.generator") version "7.1.0"
}

group = "net.kigawa.keruta"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // HTTP Client (for generated OpenAPI client)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.squareup.moshi:moshi-adapters:1.15.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk:1.13.5")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}

// Configure Gradle to automatically download the required JDK
plugins.withType<JavaPlugin> {
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}

// Configure toolchain repositories to download Java 21 automatically
repositories {
    mavenCentral()
}

// Enable auto-provisioning of JDKs with the Foojay Toolchains Plugin

tasks.withType<Test> {
    useJUnitPlatform()
}

// OpenAPI Code Generation Configuration
openApiGenerate {
    generatorName.set("kotlin")
    // 開発時はローカルファイル、CI時はAPIサーバーから取得
    inputSpec.set(project.findProperty("inputSpec") as String? ?: "${projectDir}/../keruta-api/src/main/resources/openapi.yaml")
    outputDir.set("${projectDir}/generated-api")
    apiPackage.set("net.kigawa.keruta.executor.client.api")
    modelPackage.set("net.kigawa.keruta.executor.client.model")
    packageName.set("net.kigawa.keruta.executor.client")
    configOptions.set(
        mapOf(
            "dateLibrary" to "java8",
            "serializationLibrary" to "moshi",
            "library" to "jvm-okhttp4",
            "useCoroutines" to "true"
        )
    )
}

// Add generated sources to compilation
sourceSets {
    main {
        kotlin {
            srcDir("${projectDir}/generated-api/src/main/kotlin")
        }
    }
}

// OpenAPI生成タスクをビルドから除外（CIでのみ実行）
// Dockerビルド時はOpenAPIタスクを無効化
if (System.getenv("DOCKER_BUILD") == "true" || !File("${projectDir}/../keruta-api/src/main/resources/openapi.yaml").exists()) {
    tasks.named("openApiGenerate") {
        enabled = false
    }
    tasks.named("compileKotlin") {
        // OpenAPIタスクが無効の場合は依存関係を削除
    }
} else {
    tasks.named("compileKotlin") {
        dependsOn("openApiGenerate")
    }
}

// Ensure ktlint runs after code generation and exclude generated files
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask> {
    if (System.getenv("DOCKER_BUILD") != "true" && File("${projectDir}/../keruta-api/src/main/resources/openapi.yaml").exists()) {
        dependsOn("openApiGenerate")
    }
    setSource(files("src"))
}

tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask> {
    if (System.getenv("DOCKER_BUILD") != "true" && File("${projectDir}/../keruta-api/src/main/resources/openapi.yaml").exists()) {
        dependsOn("openApiGenerate")
    }
    setSource(files("src"))
}
