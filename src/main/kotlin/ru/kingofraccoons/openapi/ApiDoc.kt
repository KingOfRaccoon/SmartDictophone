package ru.kingofraccoons.openapi

import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Аннотация для документирования endpoint'ов
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiOperation(
    val summary: String,
    val description: String = "",
    val tags: Array<String> = []
)

/**
 * Аннотация для документирования параметров запроса
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ApiParameter(
    val name: String,
    val description: String = "",
    val required: Boolean = false,
    val type: String = "string",
    val location: ParameterLocation = ParameterLocation.QUERY
)

enum class ParameterLocation {
    QUERY, PATH, HEADER, COOKIE
}

/**
 * Аннотация для документирования тела запроса
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiRequestBody(
    val description: String = "",
    val required: Boolean = true,
    val contentType: String = "application/json"
)

/**
 * Аннотация для документирования ответов
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ApiResponse(
    val code: Int,
    val description: String,
    val contentType: String = "application/json"
)

/**
 * Регистр для хранения метаданных о endpoints
 */
object ApiRegistry {
    data class EndpointInfo(
        val method: String,
        val path: String,
        val summary: String,
        val description: String,
        val tags: List<String>,
        val parameters: List<ParameterInfo>,
        val requestBody: RequestBodyInfo?,
        val responses: Map<Int, ResponseInfo>
    )
    
    data class ParameterInfo(
        val name: String,
        val description: String,
        val required: Boolean,
        val type: String,
        val location: ParameterLocation
    )
    
    data class RequestBodyInfo(
        val description: String,
        val required: Boolean,
        val contentType: String,
        val example: Any?
    )
    
    data class ResponseInfo(
        val description: String,
        val contentType: String,
        val example: Any?
    )
    
    private val endpoints = mutableListOf<EndpointInfo>()
    
    fun register(endpoint: EndpointInfo) {
        endpoints.add(endpoint)
    }
    
    fun getAllEndpoints(): List<EndpointInfo> = endpoints.toList()
    
    fun clear() {
        endpoints.clear()
    }
}

/**
 * DSL для документирования endpoint'ов
 */
class ApiDocBuilder {
    var summary: String = ""
    var description: String = ""
    var tags: List<String> = emptyList()
    val parameters = mutableListOf<ApiRegistry.ParameterInfo>()
    var requestBody: ApiRegistry.RequestBodyInfo? = null
    val responses = mutableMapOf<Int, ApiRegistry.ResponseInfo>()
    
    fun parameter(
        name: String,
        description: String = "",
        required: Boolean = false,
        type: String = "string",
        location: ParameterLocation = ParameterLocation.QUERY
    ) {
        parameters.add(
            ApiRegistry.ParameterInfo(name, description, required, type, location)
        )
    }
    
    fun requestBody(
        description: String = "",
        required: Boolean = true,
        contentType: String = "application/json",
        example: Any? = null
    ) {
        requestBody = ApiRegistry.RequestBodyInfo(description, required, contentType, example)
    }
    
    fun response(
        code: HttpStatusCode,
        description: String,
        contentType: String = "application/json",
        example: Any? = null
    ) {
        responses[code.value] = ApiRegistry.ResponseInfo(description, contentType, example)
    }
}

/**
 * Хелпер функция для документирования endpoint'а
 */
fun apiDoc(method: String, path: String, block: ApiDocBuilder.() -> Unit) {
    val builder = ApiDocBuilder()
    builder.block()
    
    ApiRegistry.register(
        ApiRegistry.EndpointInfo(
            method = method,
            path = path,
            summary = builder.summary,
            description = builder.description,
            tags = builder.tags,
            parameters = builder.parameters,
            requestBody = builder.requestBody,
            responses = builder.responses
        )
    )
}
