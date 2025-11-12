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
    private val durable = rabbitConfig.propertyOrNull("durable")?.getString()?.toBoolean() ?: true
    
    private val factory = ConnectionFactory().apply {
        this.host = this@RabbitMQService.host
        this.port = this@RabbitMQService.port
        this.username = this@RabbitMQService.username
        this.password = this@RabbitMQService.password
        this.isAutomaticRecoveryEnabled = true
    }
    
    private var connection: Connection? = null
    private var channel: Channel? = null
    
    init {
        try {
            connection = factory.newConnection()
            channel = connection?.createChannel()
            
            // Пытаемся создать очередь с durable=true
            try {
                channel?.queueDeclare(queueName, durable, false, false, null)
                logger.info { "Connected to RabbitMQ at $host:$port, queue: $queueName (durable: $durable)" }
            } catch (e: Exception) {
                // Если не удалось создать с durable=true, попробуем с durable=false
                logger.warn { "Failed to create durable queue, trying non-durable: ${e.message}" }
                try {
                    channel?.queueDeclare(queueName, false, false, false, null)
                    logger.info { "Connected to RabbitMQ at $host:$port, queue: $queueName (durable: false)" }
                } catch (e2: Exception) {
                    // Если очередь уже существует, просто логируем предупреждение
                    logger.info { "Queue $queueName already exists or accessible, proceeding..." }
                }
            }
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
            // ML-сервис ожидает просто число, а не JSON объект
            val message = recordId.toString()
            
            logger.info { "Sending message to RabbitMQ: $message" }
            
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
