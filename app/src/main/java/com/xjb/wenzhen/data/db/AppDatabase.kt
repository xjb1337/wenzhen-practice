package com.xjb.wenzhen.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConsultationEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun consultationDao(): ConsultationDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wenzhen_practice.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }

        const val MIGRATION_1_2_SQL =
            "ALTER TABLE consultations ADD COLUMN patientPersonality TEXT NOT NULL DEFAULT 'COOPERATIVE'"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(MIGRATION_1_2_SQL)
            }
        }

        const val MIGRATION_2_3_SQL = "ALTER TABLE consultations ADD COLUMN encounterType TEXT NOT NULL DEFAULT 'OUTPATIENT'"
        const val MIGRATION_2_3_SQL_TREATMENT = "ALTER TABLE consultations ADD COLUMN treatmentAnswer TEXT NOT NULL DEFAULT ''"
        const val MIGRATION_2_3_SQL_RECORD = "ALTER TABLE consultations ADD COLUMN recordType TEXT NOT NULL DEFAULT 'INPATIENT'"
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(MIGRATION_2_3_SQL)
                database.execSQL(MIGRATION_2_3_SQL_TREATMENT)
                database.execSQL(MIGRATION_2_3_SQL_RECORD)
            }
        }
    }
}
