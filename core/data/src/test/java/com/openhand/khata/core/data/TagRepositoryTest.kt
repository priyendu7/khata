package com.openhand.khata.core.data

import com.openhand.khata.core.database.entity.PayeeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagRepositoryTest : RepositoryTest() {
    private val tags by lazy { TagRepository(lazyDb) }

    @Test
    fun getOrCreateReusesTagsIgnoringCase() = runTest {
        val first = tags.getOrCreate(listOf("Work", " trip-goa ", "", "work"))
        val again = tags.getOrCreate(listOf("WORK", "trip-goa", "new"))

        assertEquals(2, first.size)
        assertEquals(first[0], again[0])
        assertEquals(first[1], again[1])
        assertEquals(listOf("new", "trip-goa", "Work"), tags.observeTags().first().map { it.name })
    }

    @Test
    fun countsUsageAndSuggestsMostUsedFirst() = runTest {
        val (travel, trip) = tags.getOrCreate(listOf("travel", "trip-goa"))
        addTransaction(tagIds = listOf(trip))
        addTransaction(tagIds = listOf(trip, travel))

        assertEquals(
            mapOf("travel" to 1, "trip-goa" to 2),
            tags.observeTags().first().associate {
                it.name to
                    it.usageCount
            }
        )
        assertEquals(listOf("trip-goa", "travel"), tags.suggestions("tr").map { it.name })
        assertEquals(listOf("trip-goa"), tags.suggestions("TRI").map { it.name })
        assertTrue(tags.suggestions("%").isEmpty())
    }

    @Test
    fun renameRefusesATakenNameSoTheUiCanOfferAMerge() = runTest {
        val (work, office) = tags.getOrCreate(listOf("work", "office"))

        assertEquals(RenameResult.Renamed, tags.rename(office, "Office"))
        assertEquals(RenameResult.Blank, tags.rename(office, " "))
        val result = tags.rename(office, "WORK")
        assertTrue(result is RenameResult.NameTaken && result.existing.id == work)
    }

    @Test
    fun mergeMovesEveryUseAndDeletesTheOldTag() = runTest {
        val (work, office) = tags.getOrCreate(listOf("work", "office"))
        val both = addTransaction(tagIds = listOf(work, office))
        val onlyOffice = addTransaction(tagIds = listOf(office))
        val payee = db.payeeDao().insert(
            PayeeEntity(identifier = "cafe@upi", displayName = "Cafe", defaultCategoryId = null)
        )
        db.payeeDao().setDefaultTags(payee, listOf(office))

        tags.merge(fromTagId = office, intoTagId = work)

        assertEquals(listOf("work"), tags.observeTags().first().map { it.name })
        assertEquals(listOf(work), db.transactionDao().tagIds(both))
        assertEquals(listOf(work), db.transactionDao().tagIds(onlyOffice))
        assertEquals(listOf(work), db.payeeDao().defaultTagIds(payee))
        assertEquals(2, tags.observeTags().first().single().usageCount)
    }

    @Test
    fun deletingATagKeepsTheTransaction() = runTest {
        val (work) = tags.getOrCreate(listOf("work"))
        val t = addTransaction(tagIds = listOf(work))

        tags.delete(work)

        assertTrue(db.transactionDao().tagIds(t).isEmpty())
        assertEquals(t, db.transactionDao().getById(t)!!.id)
    }
}
