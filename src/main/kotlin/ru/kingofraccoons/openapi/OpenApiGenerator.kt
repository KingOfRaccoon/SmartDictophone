package ru.kingofraccoons.openapi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Генератор OpenAPI спецификации из зарегистрированных endpoint'ов
 */
object OpenApiGenerator {
    
    @Serializable
    data class OpenApiSpec(
        val openapi: String,
        val info: Info,
        val servers: List<Server> = emptyList(),
        val paths: Map<String, PathItem> = emptyMap(),
        val components: Components? = null,
        val security: List<Map<String, List<String>>>? = null
    )
    
    @Serializable
    data class Info(
        val title: String,
        val version: String,
        val description: String? = null,
        val contact: Contact? = null
    )
    
    @Serializable
    data class Contact(
        val name: String? = null,
        val email: String? = null,
        val url: String? = null
    )
    
    @Serializable
    data class Server(
        val url: String,
        val description: String? = null
    )
    
    @Serializable
    data class PathItem(
        val get: Operation? = null,
        val post: Operation? = null,
        val put: Operation? = null,
        val delete: Operation? = null,
        val patch: Operation? = null
    )
    
    @Serializable
    data class Operation(
        val summary: String? = null,
        val description: String? = null,
        val tags: List<String>? = null,
        val parameters: List<Parameter>? = null,
        val requestBody: RequestBody? = null,
        val responses: Map<String, Response>,
        val security: List<Map<String, List<String>>>? = null
    )
    
    @Serializable
    data class Parameter(
        val name: String,
        val `in`: String,
        val description: String? = null,
        val required: Boolean = false,
        val schema: Schema
    )
    
    @Serializable
    data class Schema(
        val type: String,
        val format: String? = null
    )
    
    @Serializable
    data class RequestBody(
        val description: String? = null,
        val required: Boolean = true,
        val content: Map<String, MediaType>
    )
    
    @Serializable
    data class MediaType(
        val schema: Schema? = null,
        val example: JsonElement? = null
    )
    
    @Serializable
    data class Response(
        val description: String,
        val content: Map<String, MediaType>? = null
    )
    
    @Serializable
    data class Components(
        val securitySchemes: Map<String, SecurityScheme>? = null,
        val schemas: Map<String, Schema>? = null
    )
    
    @Serializable
    data class SecurityScheme(
        val type: String,
        val scheme: String? = null,
        val bearerFormat: String? = null,
        val `in`: String? = null,
        val name: String? = null,
        val description: String? = null
    )
    
    fun generateSpec(
        title: String = "API Documentation",
        version: String = "1.0.0",
        description: String = "",
        servers: List<Server> = listOf(Server("http://localhost:8888")),
        securitySchemes: Map<String, SecurityScheme>? = null,
        openapiVersion: String = "3.1.0"
    ): OpenApiSpec {
        val endpoints = ApiRegistry.getAllEndpoints()
        val paths = mutableMapOf<String, PathItem>()
        
        endpoints.groupBy { it.path }.forEach { (path, endpointsForPath) ->
            val operations = mutableMapOf<String, Operation>()
            
            endpointsForPath.forEach { endpoint ->
                val operation = Operation(
                    summary = endpoint.summary,
                    description = endpoint.description.takeIf { it.isNotBlank() },
                    tags = endpoint.tags.takeIf { it.isNotEmpty() },
                    parameters = endpoint.parameters.map { param ->
                        Parameter(
                            name = param.name,
                            `in` = param.location.name.lowercase(),
                            description = param.description.takeIf { it.isNotBlank() },
                            required = param.required,
                            schema = Schema(type = param.type)
                        )
                    }.takeIf { it.isNotEmpty() },
                    requestBody = endpoint.requestBody?.let { body ->
                        RequestBody(
                            description = body.description.takeIf { it.isNotBlank() },
                            required = body.required,
                            content = mapOf(
                                body.contentType to MediaType(
                                    schema = Schema(type = "object"),
                                    example = body.example?.let { 
                                        if (it.toString().isNotBlank()) {
                                            try {
                                                Json.parseToJsonElement(it.toString())
                                            } catch (e: Exception) {
                                                null
                                            }
                                        } else null
                                    }
                                )
                            )
                        )
                    },
                    responses = endpoint.responses.mapKeys { it.key.toString() }.mapValues { (_, response) ->
                        Response(
                            description = response.description,
                            content = mapOf(
                                response.contentType to MediaType(
                                    schema = Schema(type = "object"),
                                    example = response.example?.let {
                                        if (it.toString().isNotBlank()) {
                                            try {
                                                Json.parseToJsonElement(it.toString())
                                            } catch (e: Exception) {
                                                null
                                            }
                                        } else null
                                    }
                                )
                            )
                        )
                    }
                )
                
                operations[endpoint.method.lowercase()] = operation
            }
            
            paths[path] = PathItem(
                get = operations["get"],
                post = operations["post"],
                put = operations["put"],
                delete = operations["delete"],
                patch = operations["patch"]
            )
        }
        
        return OpenApiSpec(
            openapi = openapiVersion,
            info = Info(
                title = title,
                version = version,
                description = description.takeIf { it.isNotBlank() }
            ),
            servers = servers,
            paths = paths,
            components = securitySchemes?.let { Components(securitySchemes = it) }
        )
    }
}
