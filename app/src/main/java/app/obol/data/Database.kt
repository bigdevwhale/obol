package app.obol.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A patron (a muse or a god) is a category of useful time. Time given to their apps, sites
 * or rites earns obols at [rate] cents a minute.
 */
@Entity
data class Patron(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Key of a built-in patron, so the name follows the app language until the user renames it. */
    val preset: String? = null,
    val name: String = "",
    val glyph: String = "book",
    val hue: Int = 0,
    /** Cents a minute. */
    val rate: Long = 100,
    /** Minutes a day at the full rate; past them the rate halves. 0 = no cap. */
    val capMin: Int = 0,
    /** Counts while the patron's app plays sound with the screen off (meditations, audiobooks). */
    val audio: Boolean = false,
    val active: Boolean = true,
    val sort: Int = 0,
)

/** A stream of Lethe: a feed that costs obols to cross into. */
@Entity
data class Stream(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val preset: String? = null,
    val name: String = "",
    /** Cents a minute before the tariffs. */
    val price: Long = 200,
    /** Only the endless feed (Reels, Shorts) costs; messages and the rest of the app stay free. */
    val feedOnly: Boolean = false,
    val active: Boolean = true,
    val sort: Int = 0,
)

object Owner {
    const val PATRON = 0
    const val STREAM = 1
}

object TargetKind {
    const val APP = 0
    const val SITE = 1
}

/** An app or a site that belongs to a patron or a stream. */
@Entity(indices = [Index("owner", "ownerId")])
data class Target(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val owner: Int,
    val ownerId: Long,
    val kind: Int,
    /** A package name or a site rule such as `youtube.com/shorts`. */
    val value: String,
)

object EntryKind {
    /** Time given to a patron's app or site. */
    const val EARN = "earn"
    /** A rite: time away from the phone, by the timer. */
    const val RITE = "rite"
    /** Paying Charon. */
    const val CROSS = "cross"
    /** Hermes' staff: a free crossing. */
    const val PASS = "pass"
    /** Obols out of nowhere: the first ones on the road, corrections. */
    const val GIFT = "gift"
}

/** One line of the ledger. The balance is the sum of all of them. */
@Entity(indices = [Index("day"), Index("kind")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    /** Local epoch day. */
    val day: Long,
    val kind: String,
    /** Signed cents. */
    val cents: Long,
    val patronId: Long? = null,
    val streamId: Long? = null,
    val seconds: Int = 0,
    /** Free text: the reason given to Charon, the rite's length, "debt". */
    val note: String = "",
)

/** A paid passage across Lethe. Prepaid ones count down only while you're on the far shore. */
@Entity(indices = [Index("streamId", "open")])
data class Crossing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val streamId: Long,
    val startedAt: Long,
    val day: Long,
    /** By the meter: pay each minute as it goes instead of up front. */
    val meter: Boolean = false,
    /** Prepaid seconds left (not used by the meter). */
    val leftSec: Int = 0,
    /** Cents a minute agreed at the start (used by the meter). */
    val perMin: Long = 0,
    /** Seconds spent on the far shore. */
    val usedSec: Int = 0,
    /** The ledger line that carries what was paid; the meter keeps adding to it. */
    val entryId: Long = 0,
    val reason: String = "",
    val open: Boolean = true,
)

data class DaySum(val day: Long, val earned: Long, val spent: Long)

@Dao
interface ObolDao {
    @Query("SELECT * FROM Patron ORDER BY sort, id")
    fun observePatrons(): Flow<List<Patron>>

    @Query("SELECT * FROM Stream ORDER BY sort, id")
    fun observeStreams(): Flow<List<Stream>>

    @Query("SELECT * FROM Target ORDER BY id")
    fun observeTargets(): Flow<List<Target>>

    @Query("SELECT * FROM Patron WHERE id = :id")
    suspend fun patron(id: Long): Patron?

    @Query("SELECT * FROM Stream WHERE id = :id")
    suspend fun stream(id: Long): Stream?

    @Query("SELECT * FROM Target WHERE owner = :owner AND ownerId = :id ORDER BY id")
    suspend fun targetsOf(owner: Int, id: Long): List<Target>

    @Query("SELECT COUNT(*) FROM Patron")
    suspend fun patronCount(): Int

    @Upsert suspend fun upsertPatron(p: Patron): Long
    @Upsert suspend fun upsertStream(s: Stream): Long
    @Insert suspend fun insertTargets(t: List<Target>)

    @Query("DELETE FROM Target WHERE owner = :owner AND ownerId = :id")
    suspend fun deleteTargets(owner: Int, id: Long)

    @Query("DELETE FROM Patron WHERE id = :id")
    suspend fun deletePatronRow(id: Long)

    @Query("DELETE FROM Stream WHERE id = :id")
    suspend fun deleteStreamRow(id: Long)

    /** Saves a patron with its apps and sites; returns its id. */
    @Transaction
    suspend fun savePatron(p: Patron, targets: List<Target>): Long {
        val id = upsertPatron(p).let { if (p.id != 0L) p.id else it }
        deleteTargets(Owner.PATRON, id)
        insertTargets(targets.map { it.copy(id = 0, owner = Owner.PATRON, ownerId = id) })
        return id
    }

    @Transaction
    suspend fun saveStream(s: Stream, targets: List<Target>): Long {
        val id = upsertStream(s).let { if (s.id != 0L) s.id else it }
        deleteTargets(Owner.STREAM, id)
        insertTargets(targets.map { it.copy(id = 0, owner = Owner.STREAM, ownerId = id) })
        return id
    }

    @Transaction
    suspend fun deletePatron(id: Long) {
        deleteTargets(Owner.PATRON, id)
        deletePatronRow(id)
    }

    @Transaction
    suspend fun deleteStream(id: Long) {
        deleteTargets(Owner.STREAM, id)
        closeCrossings(id)
        deleteStreamRow(id)
    }

    // The ledger

    @Query("SELECT COALESCE(SUM(cents), 0) FROM Entry")
    fun observeBalance(): Flow<Long>

    @Query("SELECT COALESCE(SUM(cents), 0) FROM Entry")
    suspend fun balance(): Long

    @Query("SELECT * FROM Entry WHERE day = :day ORDER BY at DESC")
    fun observeDay(day: Long): Flow<List<Entry>>

    @Query(
        "SELECT day, COALESCE(SUM(CASE WHEN cents > 0 THEN cents ELSE 0 END), 0) AS earned, " +
            "COALESCE(SUM(CASE WHEN cents < 0 THEN -cents ELSE 0 END), 0) AS spent " +
            "FROM Entry WHERE day >= :from GROUP BY day ORDER BY day DESC",
    )
    fun observeDays(from: Long): Flow<List<DaySum>>

    @Insert suspend fun insertEntry(e: Entry): Long
    @Update suspend fun updateEntry(e: Entry)

    @Query("SELECT * FROM Entry WHERE id = :id")
    suspend fun entry(id: Long): Entry?

    @Query("SELECT COALESCE(SUM(seconds), 0) FROM Entry WHERE day = :day AND patronId = :patronId AND kind = 'earn'")
    suspend fun earnedSeconds(patronId: Long, day: Long): Int

    @Query("SELECT COUNT(*) FROM Entry WHERE kind = 'pass' AND day >= :from")
    suspend fun passesSince(from: Long): Int

    @Query("SELECT COUNT(*) FROM Entry WHERE kind = 'pass' AND day >= :from")
    fun observePassesSince(from: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM Crossing WHERE day = :day")
    suspend fun crossingsOn(day: Long): Int

    @Query("SELECT COUNT(*) FROM Entry WHERE kind = 'gift'")
    suspend fun giftCount(): Int

    // Crossings

    @Query("SELECT * FROM Crossing WHERE open = 1")
    suspend fun openCrossings(): List<Crossing>

    @Query("SELECT * FROM Crossing WHERE open = 1")
    fun observeOpenCrossings(): Flow<List<Crossing>>

    @Insert suspend fun insertCrossing(c: Crossing): Long
    @Update suspend fun updateCrossing(c: Crossing)

    @Query("UPDATE Crossing SET open = 0 WHERE streamId = :streamId")
    suspend fun closeCrossings(streamId: Long)

    /** Pays Charon: writes the ledger line and opens the crossing in one go. */
    @Transaction
    suspend fun cross(entry: Entry, crossing: Crossing): Crossing {
        closeCrossings(crossing.streamId)
        val entryId = insertEntry(entry)
        val c = crossing.copy(entryId = entryId)
        return c.copy(id = insertCrossing(c))
    }
}

@Database(entities = [Patron::class, Stream::class, Target::class, Entry::class, Crossing::class], version = 1, exportSchema = true)
abstract class ObolDatabase : RoomDatabase() {
    abstract fun dao(): ObolDao

    companion object {
        fun create(context: Context): ObolDatabase =
            Room.databaseBuilder(context.applicationContext, ObolDatabase::class.java, "obol.db").build()
    }
}
