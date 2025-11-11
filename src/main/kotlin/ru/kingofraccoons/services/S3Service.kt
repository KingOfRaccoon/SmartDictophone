package ru.kingofraccoons.services

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.server.application.*
import mu.KotlinLogging
import java.io.InputStream
import java.util.*

private val logger = KotlinLogging.logger {}

class S3Service(config: Application) {
    private val s3Config = config.environment.config.config("s3")
    
    private val endpoint = s3Config.property("endpoint").getString()
    private val region = s3Config.property("region").getString()
    private val accessKey = s3Config.property("accessKey").getString()
    private val secretKey = s3Config.property("secretKey").getString()
    private val bucket = s3Config.property("bucket").getString()

    private val s3Client = S3Client {
        this.region = this@S3Service.region
        endpointUrl = Url.parse(endpoint)
        credentialsProvider = StaticCredentialsProvider {
            accessKeyId = accessKey
            secretAccessKey = secretKey
        }
        forcePathStyle = true // Required for MinIO
    }

    suspend fun uploadFile(inputStream: InputStream, fileName: String, contentType: String): String {
        // ML-сервис ожидает файлы в корне bucket по имени {record_id}.m4a
        val key = if (fileName.matches(Regex("\\d+\\.m4a"))) {
            fileName  // Для аудио файлов не добавляем префикс
        } else {
            "audio/${UUID.randomUUID()}-$fileName"
        }
        
        try {
            val byteArray = inputStream.use { it.readBytes() }
            
            s3Client.putObject(PutObjectRequest {
                this.bucket = this@S3Service.bucket
                this.key = key
                this.contentType = contentType
                body = ByteStream.fromBytes(byteArray)
            })
            
            logger.info { "File uploaded successfully: $key" }
            return "${endpoint.trimEnd('/')}/$bucket/$key"
        } catch (e: Exception) {
            logger.error(e) { "Failed to upload file: $fileName" }
            throw e
        }
    }

    suspend fun downloadFile(url: String): ByteArray? {
        return try {
            val key = url.substringAfter("$bucket/")
            
            s3Client.getObject(GetObjectRequest {
                this.bucket = this@S3Service.bucket
                this.key = key
            }) { response ->
                response.body?.toByteArray()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to download file from: $url" }
            null
        }
    }

    fun close() {
        s3Client.close()
    }
}
