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

            // tables for removing columns or modifications SQLite cannot handle without dropping
            const val TABLE_CHAT_ROOM_NEW = "room_new"

            // general field names
            const val ID = "id"
        }
    }

    companion object {
        const val DATABASE_VERSION = 3

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
                    MIGRATION_2_3
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
                database.execSQL("ALTER TABLE " + TablesInfo.TABLE_USER + " ADD COLUMN is_bot INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS " + TablesInfo.TABLE_CHAT_ROOM_NEW + " (`room_id` INTEGER NOT NULL, `name` TEXT, `type` TEXT, `avatar_file_id` INTEGER, `created_at` INTEGER, `modified_at` INTEGER, `muted` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `room_exit` INTEGER, `unread_count` INTEGER NOT NULL, PRIMARY KEY(`room_id`))")
                database.execSQL("DROP TABLE " + TablesInfo.TABLE_CHAT_ROOM)
                database.execSQL("ALTER TABLE " + TablesInfo.TABLE_CHAT_ROOM_NEW + " RENAME TO " + TablesInfo.TABLE_CHAT_ROOM)

                database.execSQL("ALTER TABLE " + TablesInfo.TABLE_MESSAGE + " RENAME COLUMN type TO type_message")
                database.execSQL("ALTER TABLE " + TablesInfo.TABLE_MESSAGE + " RENAME COLUMN room_id TO room_id_message")
                database.execSQL("ALTER TABLE " + TablesInfo.TABLE_MESSAGE + " RENAME COLUMN created_at TO created_at_message")
                database.execSQL("ALTER TABLE " + TablesInfo.TABLE_MESSAGE + " RENAME COLUMN modified_at TO modified_at_message")
            }
        }

        /* Database migration from version 2 to 3.
            This part is commented out until we decide to migrate the database. Until then, we'll be
            adding new changes to the database in the comments

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE " + TablesInfo.TABLE_USER + " ADD COLUMN is_bot INTEGER NOT NULL DEFAULT 0")
            }
        } */
    }
}