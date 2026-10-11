package com.openhand.khata.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.openhand.khata.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * A named date range, such as a trip (#73): every transaction in it gets the tag [tagId]. The
 * event's name is that tag's name, so renaming the tag renames the event, and deleting the tag
 * deletes the event. Days are local epoch days, first and last inclusive. Minutes are minutes of
 * the day (0–1439) that the event starts and ends at, both included; null is the whole day (#141).
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // One event per tag: two sharing a tag would take it off each other's transactions.
    indices = [Index(value = ["tag_id"], unique = true)]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tag_id") val tagId: Long,
    @ColumnInfo(name = "start_day") val startDay: Long,
    @ColumnInfo(name = "end_day") val endDay: Long,
    @ColumnInfo(name = "start_minute") val startMinute: Int? = null,
    @ColumnInfo(name = "end_minute") val endMinute: Int? = null
)

/** An event with its tag's name. */
data class EventRow(
    val id: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
    val name: String,
    @ColumnInfo(name = "start_day") val startDay: Long,
    @ColumnInfo(name = "end_day") val endDay: Long,
    @ColumnInfo(name = "start_minute") val startMinute: Int?,
    @ColumnInfo(name = "end_minute") val endMinute: Int?
)

/** Events that include [day] at [minute]: whole days, or within the times on the first and last. */
private const val AT =
    "(e.start_day < :day OR (e.start_day = :day AND " +
        "(e.start_minute IS NULL OR e.start_minute <= :minute))) AND " +
        "(e.end_day > :day OR (e.end_day = :day AND " +
        "(e.end_minute IS NULL OR e.end_minute >= :minute)))"

private const val ROWS =
    "SELECT e.id AS id, e.tag_id AS tag_id, g.name AS name, e.start_day AS start_day, " +
        "e.end_day AS end_day, e.start_minute AS start_minute, e.end_minute AS end_minute " +
        "FROM events e JOIN tags g ON g.id = e.tag_id ORDER BY e.start_day DESC, e.id DESC"

@Dao
interface EventDao {
    /** Newest first. */
    @Query(ROWS)
    fun observeAll(): Flow<List<EventRow>>

    @Query(ROWS)
    suspend fun getAll(): List<EventRow>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE tag_id = :tagId")
    suspend fun getByTag(tagId: Long): EventEntity?

    /** Tags of the events that include [day] at [minute] of the day. */
    @Query("SELECT e.tag_id FROM events e WHERE $AT")
    suspend fun tagIdsAt(day: Long, minute: Int): List<Long>

    /** Names of the events that include [day] at [minute] of the day, by name. */
    @Query(
        "SELECT g.name FROM events e JOIN tags g ON g.id = e.tag_id " +
            "WHERE $AT ORDER BY g.name COLLATE NOCASE"
    )
    suspend fun namesAt(day: Long, minute: Int): List<String>

    @Insert suspend fun insert(event: EventEntity): Long

    @Update suspend fun update(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Tags every transaction with a timestamp in [from, until). */
    @Query(
        "INSERT OR IGNORE INTO transaction_tags (transaction_id, tag_id) " +
            "SELECT id, :tagId FROM transactions WHERE timestamp >= :from AND timestamp < :until"
    )
    suspend fun tagRange(tagId: Long, from: Long, until: Long)

    /** Takes [tagId] off every transaction with a timestamp in [from, until). */
    @Query(
        "DELETE FROM transaction_tags WHERE tag_id = :tagId AND transaction_id IN " +
            "(SELECT id FROM transactions WHERE timestamp >= :from AND timestamp < :until)"
    )
    suspend fun untagRange(tagId: Long, from: Long, until: Long)

    /** Whether any transaction, payee or event still uses [tagId]. */
    @Query(
        "SELECT EXISTS (SELECT 1 FROM transaction_tags WHERE tag_id = :tagId) " +
            "OR EXISTS (SELECT 1 FROM payee_default_tags WHERE tag_id = :tagId) " +
            "OR EXISTS (SELECT 1 FROM events WHERE tag_id = :tagId)"
    )
    suspend fun tagInUse(tagId: Long): Boolean
}
