package ru.kingofraccoons.openapi

import io.ktor.server.routing.*
import mu.KotlinLogging
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

private val logger = KotlinLogging.logger {}

/**
 * Автоматический сканер route-файлов для извлечения документации через рефлексию
 */
object RouteScanner {
    
    /**
     * Сканирует все route-файлы и автоматически регистрирует endpoints из apiDoc() вызовов
     */
    fun scanRoutes(basePackage: String = "ru.kingofraccoons.routes") {
        logger.info { "Starting automatic route scanning in package: $basePackage" }
        
        // Очищаем реестр перед сканированием
        ApiRegistry.clear()
        
        // Список классов route-файлов для сканирования
        val routeClasses = listOf(
            "ru.kingofraccoons.routes.AuthRoutesKt",
            "ru.kingofraccoons.routes.UserRoutesKt",
            "ru.kingofraccoons.routes.RecordRoutesKt",
            "ru.kingofraccoons.routes.FolderRoutesKt"
        )
        
        // Загружаем и обрабатываем каждый класс
        routeClasses.forEach { className ->
            try {
                val clazz = Class.forName(className).kotlin
                logger.info { "Scanning class: ${clazz.simpleName}" }
                scanRouteClass(clazz)
            } catch (e: ClassNotFoundException) {
                logger.warn { "Route class not found: $className" }
            } catch (e: Exception) {
                logger.error(e) { "Error scanning route class: $className" }
            }
        }
        
        logger.info { "Route scanning completed. Total endpoints: ${ApiRegistry.getAllEndpoints().size}" }
    }
    
    /**
     * Сканирует конкретный route-класс
     */
    private fun scanRouteClass(clazz: KClass<*>) {
        // Получаем все функции класса
        clazz.functions.forEach { function ->
            // Ищем функции с аннотацией @ApiOperation
            if (function.hasAnnotation<ApiOperation>()) {
                extractFromAnnotation(function)
            }
        }
    }
    
    /**
     * Извлекает информацию из аннотаций функции
     */
    private fun extractFromAnnotation(function: KFunction<*>) {
        val operation = function.annotations.filterIsInstance<ApiOperation>().firstOrNull() ?: return
        val parameters = function.annotations.filterIsInstance<ApiParameter>()
        val requestBody = function.annotations.filterIsInstance<ApiRequestBody>().firstOrNull()
        val responses = function.annotations.filterIsInstance<ApiResponse>()
        
        // Извлекаем путь и метод из имени функции (нужна дополнительная логика)
        // Пока используем fallback
        logger.debug { "Found annotated function: ${function.name}" }
    }
    
    /**
     * Парсит исходный код route-файла для извлечения apiDoc() вызовов
     */
    fun scanSourceFiles(sourcePath: String = "src/main/kotlin/ru/kingofraccoons/routes") {
        logger.info { "Scanning source files in: $sourcePath" }
        
        val sourceDir = File(sourcePath)
        if (!sourceDir.exists()) {
            logger.warn { "Source directory not found: $sourcePath" }
            return
        }
        
        // Получаем все Kotlin файлы
        val kotlinFiles = sourceDir.listFiles { file -> 
            file.isFile && file.extension == "kt" 
        } ?: emptyArray()
        
        kotlinFiles.forEach { file ->
            logger.info { "Parsing file: ${file.name}" }
            parseRouteFile(file)
        }
    }
    
    /**
     * Парсит файл и извлекает вызовы apiDoc()
     */
    private fun parseRouteFile(file: File) {
        try {
            val content = file.readText()
            
            // Простой парсинг apiDoc() вызовов через регулярные выражения
            val apiDocPattern = """apiDoc\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)\s*\{""".toRegex()
            
            val matches = apiDocPattern.findAll(content).toList()
            logger.info { "Found ${matches.size} apiDoc calls in ${file.name}" }
            
            matches.forEach { match ->
                val method = match.groupValues[1]
                val path = match.groupValues[2]
                
                logger.debug { "Processing apiDoc: $method $path in ${file.name}" }
                
                // Извлекаем блок apiDoc
                val startIndex = match.range.first
                val block = extractApiDocBlock(content, startIndex)
                
                if (block != null) {
                    try {
                        parseApiDocBlock(method, path, block)
                    } catch (e: Exception) {
                        logger.error(e) { "Error parsing apiDoc block for $method $path in ${file.name}" }
                    }
                } else {
                    logger.warn { "Could not extract apiDoc block for $method $path in ${file.name}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error parsing file: ${file.name}" }
        }
    }
    
    /**
     * Извлекает блок кода apiDoc между фигурными скобками
     * startIndex должен указывать на начало apiDoc вызова
     */
    private fun extractApiDocBlock(content: String, startIndex: Int): String? {
        // Сначала найдем закрывающую скобку ) от apiDoc(...)
        var parenCount = 0
        var foundOpenParen = false
        var closingParenIndex = -1
        
        for (i in startIndex until content.length) {
            when (content[i]) {
                '(' -> {
                    parenCount++
                    foundOpenParen = true
                }
                ')' -> {
                    if (foundOpenParen) {
                        parenCount--
                        if (parenCount == 0) {
                            closingParenIndex = i
                            break
                        }
                    }
                }
            }
        }
        
        if (closingParenIndex == -1) return null
        
        // Теперь ищем { после закрывающей скобки )
        val blockStart = content.indexOf('{', closingParenIndex)
        if (blockStart == -1) return null
        
        // Извлекаем содержимое блока между { }
        var braceCount = 0
        var inBlock = false
        
        for (i in blockStart until content.length) {
            when (content[i]) {
                '{' -> {
                    braceCount++
                    inBlock = true
                }
                '}' -> {
                    braceCount--
                    if (braceCount == 0 && inBlock) {
                        return content.substring(blockStart + 1, i)
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Парсит блок apiDoc и извлекает параметры
     */
    private fun parseApiDocBlock(method: String, path: String, block: String) {
        // Улучшенные регулярные выражения для парсинга
        val summaryPattern = """summary\s*=\s*"([^"]+)"""".toRegex()
        val descriptionPattern = """description\s*=\s*(?:[""\"]{3}([\s\S]*?)[""\"]{3}|"([^"]+)")""".toRegex()
        val tagsPattern = """tags\s*=\s*listOf\((.*?)\)""".toRegex()
        
        // Параметры с поддержкой обоих форматов: позиционные и именованные
        // parameter("name", "description", required, "type", ParameterLocation.LOCATION) - позиционный
        // parameter("name", "description", required = true, type = "string", location = ParameterLocation.LOCATION) - именованный
        val parameterPattern = """parameter\s*\(\s*"([^"]+)"\s*,\s*"([^"]*)"\s*(?:,\s*(?:required\s*=\s*)?(\w+))?\s*(?:,\s*(?:type\s*=\s*)?"([^"]+)")?\s*(?:,\s*(?:location\s*=\s*)?ParameterLocation\.(\w+))?\s*\)""".toRegex()
        
        // Response с поддержкой разных форматов
        val responsePattern = """response\s*\(\s*(?:HttpStatusCode|io\.ktor\.http\.HttpStatusCode)\.(\w+)\s*,\s*"([^"]+)"(?:\s*,\s*(?:contentType\s*=\s*)?["']([^"']+)["'])?\s*(?:,\s*example\s*=\s*[""\"]{3}([\s\S]*?)[""\"]{3})?\s*\)""".toRegex()
        
        val summary = summaryPattern.find(block)?.groupValues?.get(1) ?: ""
        
        // Улучшенное извлечение description
        val descriptionMatch = descriptionPattern.find(block)
        val description = (descriptionMatch?.groupValues?.get(1) ?: descriptionMatch?.groupValues?.get(2) ?: "").trim()
        
        // Извлекаем теги
        val tagsMatch = tagsPattern.find(block)
        val tags = if (tagsMatch != null) {
            val tagsString = tagsMatch.groupValues[1]
            tagsString.split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        
        // Извлекаем параметры с полной информацией
        val parameters = mutableListOf<ApiRegistry.ParameterInfo>()
        parameterPattern.findAll(block).forEach { match ->
            val name = match.groupValues[1]
            val desc = match.groupValues[2]
            val required = match.groupValues.getOrNull(3)?.let { it == "true" } ?: false
            val type = match.groupValues.getOrNull(4)?.takeIf { it.isNotEmpty() } ?: "string"
            val locationStr = match.groupValues.getOrNull(5)?.takeIf { it.isNotEmpty() }
            
            val location = when (locationStr?.uppercase()) {
                "HEADER" -> ParameterLocation.HEADER
                "PATH" -> ParameterLocation.PATH
                "QUERY" -> ParameterLocation.QUERY
                "COOKIE" -> ParameterLocation.COOKIE
                else -> ParameterLocation.QUERY
            }
            
            parameters.add(
                ApiRegistry.ParameterInfo(
                    name = name,
                    description = desc,
                    required = required,
                    type = type,
                    location = location
                )
            )
        }
        
        // Извлекаем responses с примерами
        val responses = mutableMapOf<Int, ApiRegistry.ResponseInfo>()
        responsePattern.findAll(block).forEach { match ->
            val statusName = match.groupValues[1]
            val desc = match.groupValues[2]
            val contentType = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() } ?: "application/json"
            val example = match.groupValues.getOrNull(4)?.trim()
            
            val statusCode = mapHttpStatusToCode(statusName)
            
            responses[statusCode] = ApiRegistry.ResponseInfo(
                description = desc,
                contentType = contentType,
                example = example
            )
        }
        
        // Проверяем requestBody с улучшенным парсингом
        val hasRequestBody = block.contains("requestBody(")
        val requestBody = if (hasRequestBody) {
            val requestBodyDescPattern = """requestBody\s*\(\s*(?:description\s*=\s*)?(?:[""\"]{3}([\s\S]*?)[""\"]{3}|"([^"]+)")""".toRegex()
            val requestBodyMatch = requestBodyDescPattern.find(block)
            val requestBodyDesc = (requestBodyMatch?.groupValues?.get(1) ?: requestBodyMatch?.groupValues?.get(2) ?: "Request body").trim()
            
            val contentTypePattern = """contentType\s*=\s*"([^"]+)"""".toRegex()
            val contentType = contentTypePattern.find(block)?.groupValues?.get(1) ?: "application/json"
            
            val examplePattern = """example\s*=\s*[""\"]{3}([\s\S]*?)[""\"]{3}""".toRegex()
            val example = examplePattern.find(block)?.groupValues?.get(1)?.trim()
            
            ApiRegistry.RequestBodyInfo(
                description = requestBodyDesc,
                required = !block.contains("required = false"),
                contentType = contentType,
                example = example
            )
        } else null
        
        // Регистрируем endpoint
        ApiRegistry.register(
            ApiRegistry.EndpointInfo(
                method = method,
                path = path,
                summary = summary,
                description = description,
                tags = tags,
                parameters = parameters,
                requestBody = requestBody,
                responses = responses
            )
        )
        
        logger.debug { "Registered endpoint: $method $path - $summary (${parameters.size} params, ${responses.size} responses)" }
    }
    
    /**
     * Маппинг имени HttpStatusCode в числовой код
     */
    private fun mapHttpStatusToCode(statusName: String): Int {
        return when (statusName) {
            "OK" -> 200
            "Created" -> 201
            "Accepted" -> 202
            "NoContent" -> 204
            "MovedPermanently" -> 301
            "Found" -> 302
            "SeeOther" -> 303
            "NotModified" -> 304
            "BadRequest" -> 400
            "Unauthorized" -> 401
            "PaymentRequired" -> 402
            "Forbidden" -> 403
            "NotFound" -> 404
            "MethodNotAllowed" -> 405
            "Conflict" -> 409
            "Gone" -> 410
            "UnprocessableEntity" -> 422
            "TooManyRequests" -> 429
            "InternalServerError" -> 500
            "NotImplemented" -> 501
            "BadGateway" -> 502
            "ServiceUnavailable" -> 503
            "GatewayTimeout" -> 504
            else -> {
                logger.warn { "Unknown HTTP status: $statusName, defaulting to 200" }
                200
            }
        }
    }
}
