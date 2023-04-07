package com.clover.studio.exampleapp.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.clover.studio.exampleapp.MainApplication
import com.clover.studio.exampleapp.data.AppDatabase.Companion.DATABASE_VERSION
import com.clover.studio.exampleapp.data.daos.*
import com.clover.studio.exampleapp.data.models.*
import com.clover.studio.exampleapp.data.models.entity.*
import com.clover.studio.exampleapp.data.models.junction.RoomUser
import com.clover.studio.exampleapp.utils.helpers.TypeConverter

@Database(
    entities = [User::class, Reaction::class, Message::class, PhoneUser::class, ChatRoom::class, MessageRecords::class, RoomUser::class, Note::class],
    version = DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(TypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun reactionDao(): ReactionDao
    abstract fun userDao(): UserDao
    abstract fun phoneUserDao(): PhoneUserDao
    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun messageRecordsDao(): MessageRecordsDao
    abstract fun notesDao(): NotesDao
    abstract fun roomUserDao(): RoomUserDao

    class TablesInfo {
        companion object {
            // list of tables
            const val TABLE_USER = "user"
            const val TABLE_REACTION = "reaction"
            const val TABLE_MESSAGE = "message"
            const val TABLE_PHONE_USER = "phone_user"
            const val TABLE_CHAT_ROOM = "room"
            const val TABLE_MESSAGE_RECORDS = "message_records"
            const val TABLE_ROOM_USER = "room_user"
            const val TABLE_NOTES = "notes"

            // general field names
            const val ID = "id"
        }
    }

    companion object {
        const val DATABASE_VERSION = 2

        @Volatile
        private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also {
                    instance = it
                }
            }

        private fun buildDatabase(appContext: Context) =
            Room.databaseBuilder(appContext, AppDatabase::class.java, "MainDatabase")
                .addMigrations(
                    MIGRATION_1_2,
                )
                .fallbackToDestructiveMigration()
                .build()

        /**
         * Use method below to clear all database tables and get a clean slate
         */
        fun nukeDb() {
            buildDatabase(MainApplication.appContext).clearAllTables()
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE " + TablesInfo.TABLE_MESSAGE_RECORDS + " ADD COLUMN record_message TEXT")
                database.execSQL("ALTER TABLE " + TablesInfo.TABLE_CHAT_ROOM + " ADD COLUMN unread_count INTEGER DEFAULT 0 NOT NULL")
            }
        }
    }
}