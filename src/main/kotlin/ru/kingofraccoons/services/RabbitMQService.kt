package ru.kingofraccoons.services

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import kotlin.concurrent.thread

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
    private val summaryQueueName = rabbitConfig.propertyOrNull("summaryQueue")?.getString() ?: "summary-tasks"

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
     * Отправляет задачу на саммаризацию в RabbitMQ очередь
     * @param recordId ID записи для саммаризации
     */
    fun sendSummaryTask(recordId: Long) {
        try {
            val message = recordId.toString()
            logger.info { "Sending summary task to RabbitMQ: $message" }
            channel?.basicPublish("", summaryQueueName, null, message.toByteArray(Charsets.UTF_8))
            logger.info { "Sent summary task for record ID: $recordId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send summary task for record ID: $recordId" }
            throw e
        }
    }

    /**
     * Запускает consumer для обработки задач саммаризации
     * @param onMessage callback для обработки полученного recordId
     */
    fun startSummaryConsumer(onMessage: suspend (Long) -> Unit) {
        thread(name = "rabbitmq-summary-consumer", isDaemon = true) {
            try {
                val consumerChannel = connection?.createChannel()
                consumerChannel?.queueDeclare(summaryQueueName, true, false, false, null)

                val consumer = object : DefaultConsumer(consumerChannel) {
                    override fun handleDelivery(
                        tag: String,
                        envelope: Envelope,
                        properties: AMQP.BasicProperties,
                        body: ByteArray
                    ) {
                        val recordId = String(body, Charsets.UTF_8).toLongOrNull()
                        if (recordId == null) {
                            consumerChannel?.basicAck(envelope.deliveryTag, false)
                            return
                        }

                        logger.info { "Received summary task for record ID: $recordId" }

                        runBlocking {
                            try {
                                onMessage(recordId)
                                consumerChannel?.basicAck(envelope.deliveryTag, false)
                                logger.info { "Summary task completed for record ID: $recordId" }
                            } catch (e: Exception) {
                                logger.error(e) { "Summary task failed for record ID: $recordId" }
                                consumerChannel?.basicNack(envelope.deliveryTag, false, false)
                            }
                        }
                    }
                }

                consumerChannel?.basicConsume(summaryQueueName, false, consumer)
                logger.info { "Started summary consumer on queue: $summaryQueueName" }

                while (!Thread.currentThread().isInterrupted) {
                    Thread.sleep(1000)
                }
            } catch (e: Exception) {
                logger.error(e) { "Summary consumer thread error" }
            }
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
