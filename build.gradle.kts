plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
}

group = "ru.kingofraccoons"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.swagger)
    
    // OpenAPI generation support
    implementation(libs.swagger.parser)
    implementation(libs.kotlin.reflect)
    implementation(libs.snakeyaml)

    // Ktor Client for Keycloak API calls
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    
    // Exposed ORM
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.java.time)
    
    // Database
    implementation(libs.postgresql)
    implementation(libs.hikari)
    
    // Security
    implementation(libs.bcrypt)
    
    // AWS S3
    implementation(libs.aws.s3)
    implementation(libs.aws.http.client)
    
    // PDF
    implementation(libs.pdfbox)
    
    // RabbitMQ
    implementation("com.rabbitmq:amqp-client:5.20.0")

    // Logging
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)
    
    // Coroutines
    implementation(libs.coroutines.core)
    
    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.h2)

    // Swagger UI
    implementation("io.ktor:ktor-server-swagger")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// Task для генерации OpenAPI спецификации
tasks.register<JavaExec>("generateOpenApi") {
    description = "Generates OpenAPI specification from code"
    group = "documentation"
    
    // Зависит от компиляции Kotlin (но не от processResources)
    dependsOn(tasks.named("compileKotlin"))
    
    // Настройка JavaExec задачи
    mainClass.set("ru.kingofraccoons.openapi.OpenApiGeneratorMainKt")
    
    // Используем только compile dependencies + скомпилированные классы
    // Это избегает циклической зависимости через :classes
    classpath = sourceSets["main"].compileClasspath + 
                files(tasks.named("compileKotlin").get().outputs)
    
    // Логирование
    doFirst {
        println("Generating OpenAPI specification...")
    }
    
    doLast {
        println("OpenAPI specification generated successfully!")
    }
}

// Генерируем OpenAPI перед сборкой ресурсов, но не создаем циклическую зависимость
tasks.named("processResources") {
    dependsOn("generateOpenApi")
    
    // Явно указываем что processResources НЕ должен зависеть от classes
    mustRunAfter("compileKotlin")
}
