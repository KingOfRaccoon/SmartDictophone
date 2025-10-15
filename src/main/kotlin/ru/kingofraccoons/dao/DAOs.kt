package ru.kingofraccoons.dao

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ru.kingofraccoons.models.*
import ru.kingofraccoons.database.dbQuery
import java.time.LocalDateTime

class UserDAO {
    suspend fun create(email: String, passwordHash: String, fullName: String): User? = dbQuery {
        val insertStatement = Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.fullName] = fullName
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToUser)
    }
    
    suspend fun createFromKeycloak(email: String, fullName: String, keycloakUserId: String): User? = dbQuery {
        val insertStatement = Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = null // No password for Keycloak users
            it[Users.fullName] = fullName
            it[Users.keycloakUserId] = keycloakUserId
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToUser)
    }

    suspend fun findByEmail(email: String): User? = dbQuery {
        Users.selectAll().where { Users.email eq email }
            .map(::resultRowToUser)
            .singleOrNull()
    }
    
    suspend fun findByKeycloakUserId(keycloakUserId: String): User? = dbQuery {
        Users.selectAll().where { Users.keycloakUserId eq keycloakUserId }
            .map(::resultRowToUser)
            .singleOrNull()
    }

    suspend fun findById(id: Long): User? = dbQuery {
        Users.selectAll().where { Users.id eq id }
            .map(::resultRowToUser)
            .singleOrNull()
    }

    suspend fun getPasswordHash(email: String): String? = dbQuery {
        Users.select(Users.passwordHash)
            .where { Users.email eq email }
            .map { it[Users.passwordHash] }
            .singleOrNull()
    }

    suspend fun existsByEmail(email: String): Boolean = dbQuery {
        Users.selectAll().where { Users.email eq email }.count() > 0
    }

    private fun resultRowToUser(row: ResultRow) = User(
        id = row[Users.id].value,
        email = row[Users.email],
        fullName = row[Users.fullName],
        createdAt = row[Users.createdAt].toString(),
        updatedAt = row[Users.updatedAt].toString()
    )
}

class FolderDAO {
    suspend fun create(userId: Long, name: String, description: String?): Folder? = dbQuery {
        val insertStatement = Folders.insert {
            it[Folders.userId] = userId
            it[Folders.name] = name
            it[Folders.description] = description
        }
        insertStatement.resultedValues?.singleOrNull()?.let(::resultRowToFolder)
    }

    suspend fun findByUserId(userId: Long): List<Folder> = dbQuery {
        Folders.selectAll().where { Folders.userId eq userId }
            .map(::resultRowToFolder)
    }

    suspend fun findById(id: Long): Folder? = dbQuery {
        Folders.selectAll().where { Folders.id eq id }
            .map(::resultRowToFolder)
            .singleOrNull()
    }

    suspend fun update(id: Long, name: String, description: String?): Folder? = dbQuery {
        Folders.update({ Folders.id eq id }) {
            it[Folders.name] = name
            it[Folders.description] = description
            it[Folders.updatedAt] = LocalDateTime.now()
        }
        findById(id)
    }

    suspend fun delete(id: Long): Boolean = dbQuery {
        Folders.deleteWhere { Folders.id eq id } > 0
    }

    private fun resultRowToFolder(row: ResultRow) = Folder(
        id = row[Folders.id].value,
        userId = row[Folders.userId].value,
        name = row[Folders.name],
        description = row[Folders.description],
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

    suspend fun search(
        userId: Long,
        search: String?,
        folderId: Long?,
        page: Int,
        size: Int
    ): Pair<List<Record>, Long> = dbQuery {
        val query = (Records innerJoin Folders)
            .selectAll()
            .where { Folders.userId eq userId }

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

    suspend fun countByUserId(userId: Long): Long = dbQuery {
        (Records innerJoin Folders)
            .selectAll()
            .where { Folders.userId eq userId }
            .count()
    }

    suspend fun sumDurationByUserId(userId: Long): Long = dbQuery {
        val result = (Records innerJoin Folders)
            .select(Records.duration.sum())
            .where { Folders.userId eq userId }
            .map { it[Records.duration.sum()] }
            .firstOrNull()
        
        result?.toLong() ?: 0L
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
