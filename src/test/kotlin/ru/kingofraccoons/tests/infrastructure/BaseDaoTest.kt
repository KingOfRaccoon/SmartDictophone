package ru.kingofraccoons.tests.infrastructure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.kingofraccoons.dao.FolderDAO
import ru.kingofraccoons.dao.RecordDAO
import ru.kingofraccoons.dao.SharedRecordDAO
import ru.kingofraccoons.dao.TranscriptionDAO
import ru.kingofraccoons.dao.UserProfileDAO
import ru.kingofraccoons.models.Folders
import ru.kingofraccoons.models.ProcessingStatuses
import ru.kingofraccoons.models.Records
import ru.kingofraccoons.models.SharedRecords
import ru.kingofraccoons.models.Summaries
import ru.kingofraccoons.models.TranscriptionSegments
import ru.kingofraccoons.models.UserProfiles

@Testcontainers
abstract class BaseDaoTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("postgres:16-alpine")
        ).apply {
            withDatabaseName("smart_dictophone_test")
            withUsername("postgres")
            withPassword("postgres")
        }

        @BeforeAll
        @JvmStatic
        fun initDatabase() {
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                driverClassName = postgres.driverClassName
                username = postgres.username
                password = postgres.password
                maximumPoolSize = 5
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            }
            Database.connect(HikariDataSource(hikariConfig))
        }
    }

    protected val folderDAO = FolderDAO()
    protected val recordDAO = RecordDAO()
    protected val transcriptionDAO = TranscriptionDAO()
    protected val userProfileDAO = UserProfileDAO()
    protected val sharedRecordDAO = SharedRecordDAO()

    @BeforeEach
    fun ensureSchemaAndClean() {
        transaction {
            @Suppress("DEPRECATION")
            SchemaUtils.createMissingTablesAndColumns(
                UserProfiles, Folders, Records,
                TranscriptionSegments, SharedRecords, ProcessingStatuses, Summaries
            )
            exec("TRUNCATE TABLE shared_records, transcription_segments, records, folders, user_profiles, processing_statuses, summaries RESTART IDENTITY CASCADE")
        }
    }
}
