package ru.kingofraccoons.openapi

import io.swagger.parser.OpenAPIParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import java.io.File

/**
 * Тесты для валидации сгенерированной OpenAPI спецификации
 */
class OpenApiGenerationTest {
    
    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            // Генерируем спецификацию перед запуском тестов
            println("Generating OpenAPI specification for tests...")
            ru.kingofraccoons.openapi.main()
        }
    }
    
    @Test
    fun `generated OpenAPI YAML should exist`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        assertTrue(yamlFile.exists(), "OpenAPI YAML file should exist")
        assertTrue(yamlFile.length() > 0, "OpenAPI YAML file should not be empty")
    }
    
    @Test
    fun `generated OpenAPI JSON should exist`() {
        val jsonFile = File("src/main/resources/openapi/documentation.json")
        assertTrue(jsonFile.exists(), "OpenAPI JSON file should exist")
        assertTrue(jsonFile.length() > 0, "OpenAPI JSON file should not be empty")
    }
    
    @Test
    fun `generated OpenAPI spec should be valid`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        
        assertNotNull(result.openAPI, "OpenAPI specification should be parsed successfully")
        assertTrue(result.messages.isEmpty(), "OpenAPI specification should have no validation errors. Errors: ${result.messages}")
    }
    
    @Test
    fun `OpenAPI spec should have correct version`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        assertNotNull(openAPI)
        assertEquals("3.1.0", openAPI.openapi, "OpenAPI version should be 3.1.0")
    }
    
    @Test
    fun `OpenAPI spec should have API info`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        assertNotNull(openAPI.info)
        assertEquals("Smart Dictophone API", openAPI.info.title)
        assertEquals("1.0.0", openAPI.info.version)
        assertNotNull(openAPI.info.description)
    }
    
    @Test
    fun `OpenAPI spec should have servers`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        assertNotNull(openAPI.servers)
        assertTrue(openAPI.servers.isNotEmpty(), "Should have at least one server")
        assertTrue(
            openAPI.servers.any { it.url == "http://localhost:8888" },
            "Should have localhost server"
        )
    }
    
    @Test
    fun `OpenAPI spec should have security schemes`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        assertNotNull(openAPI.components)
        assertNotNull(openAPI.components.securitySchemes)
        assertTrue(
            openAPI.components.securitySchemes.containsKey("BearerAuth"),
            "Should have BearerAuth security scheme"
        )
        assertTrue(
            openAPI.components.securitySchemes.containsKey("ApiKeyAuth"),
            "Should have ApiKeyAuth security scheme"
        )
    }
    
    @Test
    fun `OpenAPI spec should have paths`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        assertNotNull(openAPI.paths)
        assertTrue(openAPI.paths.isNotEmpty(), "Should have at least one path")
    }
    
    @Test
    fun `OpenAPI spec should have authentication endpoints`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        // Auth endpoints без /auth префикса (как в реальном коде)
        assertTrue(
            openAPI.paths.containsKey("/register"),
            "Should have /register endpoint"
        )
        assertTrue(
            openAPI.paths.containsKey("/login"),
            "Should have /login endpoint"
        )
        assertTrue(
            openAPI.paths.containsKey("/refresh"),
            "Should have /refresh endpoint"
        )
    }
    
    @Test
    fun `OpenAPI spec should have user endpoints`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        assertTrue(
            openAPI.paths.containsKey("/recordInfo"),
            "Should have /recordInfo endpoint"
        )
    }
    
    @Test
    fun `OpenAPI spec should have record endpoints`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        assertTrue(
            openAPI.paths.containsKey("/records"),
            "Should have /records endpoint"
        )
        // /records/{id} не реализован, есть только подпути
        assertTrue(
            openAPI.paths.containsKey("/records/{id}/audio"),
            "Should have /records/{id}/audio endpoint"
        )
        assertTrue(
            openAPI.paths.containsKey("/records/{id}/pdf"),
            "Should have /records/{id}/pdf endpoint"
        )
        assertTrue(
            openAPI.paths.containsKey("/records/{id}/transcribe"),
            "Should have /records/{id}/transcribe endpoint"
        )
    }
    
    @Test
    fun `OpenAPI spec should have folder endpoints`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        assertTrue(
            openAPI.paths.containsKey("/folders"),
            "Should have /folders endpoint"
        )
        assertTrue(
            openAPI.paths.containsKey("/folders/{id}"),
            "Should have /folders/{id} endpoint"
        )
    }
    
    @Test
    fun `OpenAPI spec should have operations with tags`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        val allTags = mutableSetOf<String>()
        
        openAPI.paths.values.forEach { pathItem ->
            listOfNotNull(
                pathItem.get,
                pathItem.post,
                pathItem.put,
                pathItem.delete,
                pathItem.patch
            ).forEach { operation ->
                operation.tags?.let { allTags.addAll(it) }
            }
        }
        
        assertTrue(allTags.contains("Authentication"), "Should have Authentication tag")
        assertTrue(allTags.contains("Users"), "Should have Users tag")
        assertTrue(allTags.contains("Records"), "Should have Records tag")
        assertTrue(allTags.contains("Folders"), "Should have Folders tag")
    }
    
    @Test
    fun `OpenAPI spec should have responses for all operations`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        openAPI.paths.values.forEach { pathItem ->
            listOfNotNull(
                pathItem.get,
                pathItem.post,
                pathItem.put,
                pathItem.delete,
                pathItem.patch
            ).forEach { operation ->
                assertNotNull(operation.responses, "Operation should have responses")
                assertTrue(
                    operation.responses.isNotEmpty(),
                    "Operation should have at least one response"
                )
            }
        }
    }
    
    @Test
    fun `all registered endpoints should be in specification`() {
        val yamlFile = File("src/main/resources/openapi/documentation.yaml")
        val content = yamlFile.readText()
        
        val result = OpenAPIParser().readContents(content, null, null)
        val openAPI = result.openAPI
        
        val registeredCount = ApiRegistry.getAllEndpoints().size
        
        val specEndpointsCount = openAPI.paths.values.sumOf { pathItem ->
            listOfNotNull(
                pathItem.get,
                pathItem.post,
                pathItem.put,
                pathItem.delete,
                pathItem.patch
            ).size
        }
        
        assertEquals(
            registeredCount,
            specEndpointsCount,
            "All registered endpoints should be in the specification"
        )
    }
}
