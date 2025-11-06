package ru.kingofraccoons.dao

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ru.kingofraccoons.models.*
import ru.kingofraccoons.database.dbQuery
import java.time.LocalDateTime

// UserDAO removed - using Keycloak user IDs directly

class FolderDAO {
    /**
     * Создание папки для пользователя
     */
    suspend fun create(keycloakUserId: String, name: String, description: String?, isDefault: Boolean = false): Folder? = dbQuery {
        val insertStatement = Folders.insert {
            it[Folders.keycloakUserId] = keycloakUserId
            it[Folders.name] = name
            it[Folders.description] = description
            it[Folders.isDefault] = isDefault
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToFolder)
    }

    /**
     * Создание дефолтных папок для пользователя при первой авторизации
     */
    suspend fun createDefaultFolders(keycloakUserId: String): List<Folder> = dbQuery {
        val defaultFolders = listOf("Работа", "Учёба", "Личное")
        defaultFolders.mapNotNull { folderName ->
            val insertStatement = Folders.insert {
                it[Folders.keycloakUserId] = keycloakUserId
                it[name] = folderName
                it[description] = null
                it[isDefault] = true
            }
            insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToFolder)
        }
    }

    /**
     * Получение всех папок пользователя
     */
    suspend fun findByKeycloakUserId(keycloakUserId: String): List<Folder> = dbQuery {
        Folders.selectAll().where { Folders.keycloakUserId eq keycloakUserId }
            .map(::resultRowToFolder)
    }

    /**
     * Проверка существования дефолтных папок у пользователя
     */
    suspend fun hasDefaultFolders(keycloakUserId: String): Boolean = dbQuery {
        Folders.selectAll()
            .where { (Folders.keycloakUserId eq keycloakUserId) and (Folders.isDefault eq true) }
            .count() >= 3
    }

    /**
     * Получение папки по ID
     */
    suspend fun findById(id: Long): Folder? = dbQuery {
        Folders.selectAll().where { Folders.id eq id }
            .map(::resultRowToFolder)
            .singleOrNull()
    }

    /**
     * Обновление папки
     */
    suspend fun update(id: Long, name: String, description: String?): Folder? = dbQuery {
        Folders.update({ Folders.id eq id }) {
            it[Folders.name] = name
            it[Folders.description] = description
            it[Folders.updatedAt] = LocalDateTime.now()
        }
        findById(id)
    }

    /**
     * Удаление папки
     */
    suspend fun delete(id: Long): Boolean = dbQuery {
        Folders.deleteWhere { Folders.id eq id } > 0
    }

    private fun resultRowToFolder(row: ResultRow) = Folder(
        id = row[Folders.id].value,
        keycloakUserId = row[Folders.keycloakUserId],
        name = row[Folders.name],
        description = row[Folders.description],
        isDefault = row[Folders.isDefault],
        createdAt = row[Folders.createdAt].toString(),
        updatedAt = row[Folders.updatedAt].toString()
    )
}

class RecordDAO {
    suspend fun create(
        folderId: Long?,
        title: String,
        description: String?,
        datetime: LocalDateTime,
        latitude: Float?,
        longitude: Float?,
        duration: Int,
        category: RecordCategory,
        audioUrl: String
    ): Record? = dbQuery {
        val insertStatement = Records.insert {
            it[Records.folderId] = folderId
            it[Records.title] = title
            it[Records.description] = description
            it[Records.datetime] = datetime
            it[Records.latitude] = latitude
            it[Records.longitude] = longitude
            it[Records.duration] = duration
            it[Records.category] = category
            it[Records.audioUrl] = audioUrl
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToRecord)
    }

    suspend fun findById(id: Long): Record? = dbQuery {
        Records.selectAll().where { Records.id eq id }
            .map(::resultRowToRecord)
            .singleOrNull()
    }

    /**
     * Поиск записей пользователя с фильтрацией
     */
    suspend fun search(
        keycloakUserId: String,
        search: String?,
        folderId: Long?,
        page: Int,
        size: Int
    ): Pair<List<Record>, Long> = dbQuery {
        val query = (Records innerJoin Folders)
            .selectAll()
            .where { Folders.keycloakUserId eq keycloakUserId }

        // Apply filters
        search?.let {
            query.andWhere {
                (Records.title like "%$it%") or (Records.description like "%$it%")
            }
        }

        folderId?.let {
            query.andWhere { Records.folderId eq it }
        }

        val totalCount = query.count()
        val records = query
            .orderBy(Records.datetime to SortOrder.DESC)
            .limit(size)
            .offset((page * size).toLong())
            .map(::resultRowToRecord)

        records to totalCount
    }

    /**
     * Подсчет общего количества записей пользователя
     */
    suspend fun countByKeycloakUserId(keycloakUserId: String): Long = dbQuery {
        (Records innerJoin Folders)
            .selectAll()
            .where { Folders.keycloakUserId eq keycloakUserId }
            .count()
    }

    /**
     * Подсчет общей длительности записей пользователя (в секундах)
     */
    suspend fun sumDurationByKeycloakUserId(keycloakUserId: String): Long = dbQuery {
        val result = (Records innerJoin Folders)
            .select(Records.duration.sum())
            .where { Folders.keycloakUserId eq keycloakUserId }
            .map { it[Records.duration.sum()] }
            .firstOrNull()
        
        result?.toLong() ?: 0L
    }

    /**
     * Обновление записи
     */
    suspend fun update(
        id: Long,
        title: String,
        description: String?,
        datetime: LocalDateTime,
        latitude: Float?,
        longitude: Float?,
        duration: Int,
        category: RecordCategory,
        audioUrl: String
    ): Record? = dbQuery {
        Records.update({ Records.id eq id }) {
            it[Records.title] = title
            it[Records.description] = description
            it[Records.datetime] = datetime
            it[Records.latitude] = latitude
            it[Records.longitude] = longitude
            it[Records.duration] = duration
            it[Records.category] = category
            it[Records.audioUrl] = audioUrl
        }
        findById(id)
    }

    /**
     * Удаление записи
     */
    suspend fun delete(id: Long): Boolean = dbQuery {
        Records.deleteWhere { Records.id eq id } > 0
    }

    private fun resultRowToRecord(row: ResultRow) = Record(
        id = row[Records.id].value,
        folderId = row[Records.folderId]?.value,
        title = row[Records.title],
        description = row[Records.description],
        datetime = row[Records.datetime].toString(),
        latitude = row[Records.latitude],
        longitude = row[Records.longitude],
        duration = row[Records.duration],
        category = row[Records.category].name,
        audioUrl = row[Records.audioUrl],
        createdAt = row[Records.createdAt].toString(),
        updatedAt = row[Records.updatedAt].toString()
    )
}

class TranscriptionDAO {
    suspend fun createBatch(recordId: Long, segments: List<TranscriptionSegmentInput>): List<TranscriptionSegment> = dbQuery {
        TranscriptionSegments.batchInsert(segments) { segment ->
            this[TranscriptionSegments.recordId] = recordId
            this[TranscriptionSegments.start] = segment.start
            this[TranscriptionSegments.end] = segment.end
            this[TranscriptionSegments.text] = segment.text
        }.map(::resultRowToSegment)
    }

    suspend fun findByRecordId(recordId: Long): List<TranscriptionSegment> = dbQuery {
        TranscriptionSegments.selectAll().where { TranscriptionSegments.recordId eq recordId }
            .orderBy(TranscriptionSegments.start to SortOrder.ASC)
            .map(::resultRowToSegment)
    }

    private fun resultRowToSegment(row: ResultRow) = TranscriptionSegment(
        id = row[TranscriptionSegments.id].value,
        recordId = row[TranscriptionSegments.recordId].value,
        start = row[TranscriptionSegments.start],
        end = row[TranscriptionSegments.end],
        text = row[TranscriptionSegments.text]
    )
}
