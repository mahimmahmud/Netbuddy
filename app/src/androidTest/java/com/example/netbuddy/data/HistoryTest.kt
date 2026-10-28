package com.example.netbuddy.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryTest {
    @Test fun insertRetrieveDeleteClear() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NetBuddyDatabase::class.java).build()
        try {
            val dao = db.tests()
            val id = dao.insert(TestRecord(type = "DNS", target = "example.com", time = 1, status = "Success", summary = "one record", details = "result"))
            dao.insert(TestRecord(type = "HTTPS", target = "https://example.com", time = 2, status = "Success", summary = "HTTP 200", details = "result"))
            val records = dao.observe().first()
            assertEquals(2, records.size)
            assertEquals("HTTPS", records.first().type)
            dao.delete(id)
            assertEquals(1, dao.observe().first().size)
            dao.clear()
            assertTrue(dao.observe().first().isEmpty())
        } finally { db.close() }
    }
    @Test fun historySurvivesReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-persistence-test.db"
        context.deleteDatabase(name)
        try {
            val first = Room.databaseBuilder(context, NetBuddyDatabase::class.java, name).build()
            try { first.tests().insert(TestRecord(type = "TCP", target = "example.com:443", status = "Success", summary = "5/5", details = "saved")) }
            finally { first.close() }
            val second = Room.databaseBuilder(context, NetBuddyDatabase::class.java, name).build()
            try { assertEquals("saved", second.tests().observe().first().single().details) }
            finally { second.close() }
        } finally { context.deleteDatabase(name) }
    }
}
