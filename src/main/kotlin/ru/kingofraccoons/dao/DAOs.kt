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
        val defaultFolders = RecordCategory.entries.map { it.displayName }
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

    suspend fun findByFolderId(folderId: Long): List<Record> = dbQuery {
        Records.selectAll().where { Records.folderId eq folderId }
            .map(::resultRowToRecord)
    }

    /**
     * Поиск записей пользователя с фильтрацией
     */
    suspend fun search(
        keycloakUserId: String,
        search: String?,
        folderId: Long?,
        category: RecordCategory?,
        page: Int,
        size: Int
    ): Pair<List<Record>, Long> = dbQuery {
        val query = (Records innerJoin Folders)
            .selectAll()
            .where { Folders.keycloakUserId eq keycloakUserId }

        search?.let {
            query.andWhere {
                (Records.title like "%$it%") or (Records.description like "%$it%")
            }
        }

        folderId?.let {
            query.andWhere { Records.folderId eq it }
        }

        category?.let {
            query.andWhere { Records.category eq it }
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
     * Частичное обновление записи (только переданные поля)
     */
    suspend fun updatePartial(
        id: Long,
        title: String?,
        description: String?,
        category: RecordCategory?
    ): Record? = dbQuery {
        val hasUpdates = title != null || description != null || category != null
        if (hasUpdates) {
            Records.update({ Records.id eq id }) {
                title?.let { v -> it[Records.title] = v }
                description?.let { v -> it[Records.description] = v }
                category?.let { v -> it[Records.category] = v }
                it[Records.updatedAt] = LocalDateTime.now()
            }
        }
        findById(id)
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
     * Обновляет только описание записи (используется при сохранении транскрипции)
     */
    suspend fun updateDescription(id: Long, description: String?): Record? = dbQuery {
        Records.update({ Records.id eq id }) {
            it[Records.description] = description
            it[Records.updatedAt] = LocalDateTime.now()
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

    suspend fun deleteByRecordId(recordId: Long): Int = dbQuery {
        TranscriptionSegments.deleteWhere { TranscriptionSegments.recordId eq recordId }
    }

    private fun resultRowToSegment(row: ResultRow) = TranscriptionSegment(
        id = row[TranscriptionSegments.id].value,
        recordId = row[TranscriptionSegments.recordId].value,
        start = row[TranscriptionSegments.start],
        end = row[TranscriptionSegments.end],
        text = row[TranscriptionSegments.text]
    )
}

class UserProfileDAO {
    suspend fun findByKeycloakUserId(keycloakUserId: String): UserProfile? = dbQuery {
        UserProfiles.selectAll().where { UserProfiles.keycloakUserId eq keycloakUserId }
            .map(::resultRowToUserProfile)
            .singleOrNull()
    }

    suspend fun create(
        keycloakUserId: String,
        telegram: String? = null,
        avatarUrl: String? = null,
        emailForTranscripts: String? = null
    ): UserProfile = dbQuery {
        val insertStatement = UserProfiles.insert {
            it[UserProfiles.keycloakUserId] = keycloakUserId
            it[UserProfiles.telegram] = telegram
            it[UserProfiles.avatarUrl] = avatarUrl
            it[UserProfiles.emailForTranscripts] = emailForTranscripts
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToUserProfile)
            ?: throw IllegalStateException("Failed to create user profile")
    }

    suspend fun findOrCreate(keycloakUserId: String): UserProfile =
        findByKeycloakUserId(keycloakUserId) ?: create(keycloakUserId)

    suspend fun update(
        keycloakUserId: String,
        telegram: String? = null,
        avatarUrl: String? = null,
        emailForTranscripts: String? = null
    ): UserProfile? = dbQuery {
        val hasUpdates = telegram != null || avatarUrl != null || emailForTranscripts != null
        if (hasUpdates) {
            UserProfiles.update({ UserProfiles.keycloakUserId eq keycloakUserId }) {
                telegram?.let { v -> it[UserProfiles.telegram] = v }
                avatarUrl?.let { v -> it[UserProfiles.avatarUrl] = v }
                emailForTranscripts?.let { v -> it[UserProfiles.emailForTranscripts] = v }
                it[UserProfiles.updatedAt] = LocalDateTime.now()
            }
        }
        findByKeycloakUserId(keycloakUserId)
    }

    suspend fun updateAvatarUrl(keycloakUserId: String, avatarUrl: String): UserProfile? = dbQuery {
        UserProfiles.update({ UserProfiles.keycloakUserId eq keycloakUserId }) {
            it[UserProfiles.avatarUrl] = avatarUrl
            it[UserProfiles.updatedAt] = LocalDateTime.now()
        }
        findByKeycloakUserId(keycloakUserId)
    }

    private fun resultRowToUserProfile(row: ResultRow) = UserProfile(
        id = row[UserProfiles.id].value,
        keycloakUserId = row[UserProfiles.keycloakUserId],
        telegram = row[UserProfiles.telegram],
        avatarUrl = row[UserProfiles.avatarUrl],
        emailForTranscripts = row[UserProfiles.emailForTranscripts],
        createdAt = row[UserProfiles.createdAt].toString(),
        updatedAt = row[UserProfiles.updatedAt].toString()
    )
}

class SharedRecordDAO {
    suspend fun share(recordId: Long, sharedByUserId: String, sharedWithUserId: String, role: String = "viewer"): SharedRecord = dbQuery {
        SharedRecords.upsert(SharedRecords.recordId, SharedRecords.sharedWithUserId) {
            it[SharedRecords.recordId] = recordId
            it[SharedRecords.sharedByUserId] = sharedByUserId
            it[SharedRecords.sharedWithUserId] = sharedWithUserId
            it[SharedRecords.role] = role
        }

        SharedRecords.selectAll().where {
            (SharedRecords.recordId eq recordId) and (SharedRecords.sharedWithUserId eq sharedWithUserId)
        }.singleOrNull()?.let(::resultRowToSharedRecord)
            ?: throw IllegalStateException("Failed to create shared record")
    }

    suspend fun findByRecordId(recordId: Long): List<SharedRecord> = dbQuery {
        SharedRecords.selectAll().where { SharedRecords.recordId eq recordId }
            .map(::resultRowToSharedRecord)
    }

    suspend fun findBySharedWithUserId(userId: String): List<SharedRecord> = dbQuery {
        SharedRecords.selectAll().where { SharedRecords.sharedWithUserId eq userId }
            .map(::resultRowToSharedRecord)
    }

    suspend fun delete(recordId: Long, sharedWithUserId: String): Boolean = dbQuery {
        SharedRecords.deleteWhere {
            (SharedRecords.recordId eq recordId) and (SharedRecords.sharedWithUserId eq sharedWithUserId)
        } > 0
    }

    suspend fun deleteByRecordId(recordId: Long): Int = dbQuery {
        SharedRecords.deleteWhere { SharedRecords.recordId eq recordId }
    }

    suspend fun hasAccess(recordId: Long, userId: String): Boolean = dbQuery {
        SharedRecords.selectAll().where {
            (SharedRecords.recordId eq recordId) and (SharedRecords.sharedWithUserId eq userId)
        }.count() > 0
    }

    private fun resultRowToSharedRecord(row: ResultRow) = SharedRecord(
        id = row[SharedRecords.id].value,
        recordId = row[SharedRecords.recordId].value,
        sharedByUserId = row[SharedRecords.sharedByUserId],
        sharedWithUserId = row[SharedRecords.sharedWithUserId],
        role = row[SharedRecords.role],
        createdAt = row[SharedRecords.createdAt].toString()
    )
}
