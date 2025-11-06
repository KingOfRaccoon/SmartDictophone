package ru.kingofraccoons.services

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import io.ktor.server.application.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Serializable
data class TranscriptionTask(
    val record_id: Long
)

class RabbitMQService(config: Application) {
    private val rabbitConfig = config.environment.config.config("rabbitmq")
    
    private val host = rabbitConfig.property("host").getString()
    private val port = rabbitConfig.property("port").getString().toInt()
    private val username = rabbitConfig.property("username").getString()
    private val password = rabbitConfig.property("password").getString()
    private val queueName = rabbitConfig.property("queue").getString()
    
    private val factory = ConnectionFactory().apply {
        this.host = this@RabbitMQService.host
        this.port = this@RabbitMQService.port
        this.username = this@RabbitMQService.username
        this.password = this@RabbitMQService.password
    }
    
    private var connection: Connection? = null
    private var channel: Channel? = null
    
    init {
        try {
            connection = factory.newConnection()
            channel = connection?.createChannel()
            channel?.queueDeclare(queueName, true, false, false, null)
            logger.info { "Connected to RabbitMQ at $host:$port, queue: $queueName" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to RabbitMQ" }
        }
    }
    
    /**
     * Отправляет задачу на транскрипцию в RabbitMQ очередь
     * @param recordId ID записи для транскрипции
     */
    fun sendTranscriptionTask(recordId: Long) {
        try {
            val task = TranscriptionTask(record_id = recordId)
            val message = Json.encodeToString(task)
            
            channel?.basicPublish(
                "",
                queueName,
                null,
                message.toByteArray(Charsets.UTF_8)
            )
            
            logger.info { "Sent transcription task for record ID: $recordId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send transcription task for record ID: $recordId" }
            throw e
        }
    }
    
    /**
     * Закрывает соединение с RabbitMQ
     */
    fun close() {
        try {
            channel?.close()
            connection?.close()
            logger.info { "Closed RabbitMQ connection" }
        } catch (e: Exception) {
            logger.error(e) { "Error closing RabbitMQ connection" }
        }
    }
}
