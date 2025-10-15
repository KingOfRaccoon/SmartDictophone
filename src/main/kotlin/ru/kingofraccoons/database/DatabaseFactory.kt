package ru.kingofraccoons.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import ru.kingofraccoons.models.Folders
import ru.kingofraccoons.models.Records
import ru.kingofraccoons.models.TranscriptionSegments
import ru.kingofraccoons.models.Users

object DatabaseFactory {
    fun init(config: Application) {
        val dbConfig = config.environment.config.config("database")

        val driver = dbConfig.propertyOrNull("driver")?.getString()?.trim().takeUnless { it.isNullOrEmpty() }

        val jdbcUrlValue = dbConfig.property("url").getString()

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = jdbcUrlValue
            driver?.let { driverClassName = it }
            username = dbConfig.property("user").getString()
            password = dbConfig.property("password").getString()
            maximumPoolSize = dbConfig.propertyOrNull("maxPoolSize")?.getString()?.toInt() ?: 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        Database.connect(HikariDataSource(hikariConfig))

        // Ensure schema is up-to-date without dropping data.
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users, Folders, Records, TranscriptionSegments)
        }
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
