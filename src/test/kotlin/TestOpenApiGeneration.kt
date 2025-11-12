import io.ktor.http.*
import ru.kingofraccoons.openapi.ApiRegistry
import ru.kingofraccoons.openapi.OpenApiGenerator
import ru.kingofraccoons.openapi.ParameterLocation
import ru.kingofraccoons.openapi.apiDoc

/**
 * Тест для проверки генерации OpenAPI спецификации
 */
fun main() {
    // Очищаем реестр перед тестом
    ApiRegistry.clear()
    
    // Регистрируем тестовый endpoint
    apiDoc("GET", "/test") {
        summary = "Тестовый эндпоинт"
        description = "Это тестовый эндпоинт для проверки генерации OpenAPI"
        tags = listOf("Test")
        parameter("id", "ID элемента", true, "integer", ParameterLocation.PATH)
        parameter("name", "Имя", false, "string", ParameterLocation.QUERY)
        response(HttpStatusCode.OK, "Успешный ответ", "application/json") {
            """{"status": "ok"}"""
        }
        response(HttpStatusCode.NotFound, "Не найдено")
    }
    
    // Генерируем спецификацию
    val spec = OpenApiGenerator.generateSpec()
    
    // Выводим результат
    println("=== Generated OpenAPI Specification ===")
    println(spec)
    println()
    println("=== Registered Endpoints ===")
    ApiRegistry.getAllEndpoints().forEach { endpoint ->
        println("${endpoint.method} ${endpoint.path} - ${endpoint.summary}")
        println("  Tags: ${endpoint.tags.joinToString(", ")}")
        println("  Parameters: ${endpoint.parameters.size}")
        println("  Responses: ${endpoint.responses.keys.sorted().joinToString(", ")}")
        println()
    }
    
    println("✅ OpenAPI generation test completed successfully!")
}
