package nl.constantdynamics.everyday.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SerieEntiteit::class,
        FotoEntiteit::class,
        DagKeuzeEntiteit::class,
        BackupTaakEntiteit::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Omzetters::class)
abstract class EverydayDatabase : RoomDatabase() {
    abstract fun serieDao(): SerieDao
    abstract fun fotoDao(): FotoDao
    abstract fun dagKeuzeDao(): DagKeuzeDao
    abstract fun backupTaakDao(): BackupTaakDao
}

/** Versie 2 voegt de kopieerwachtrij voor de backupmap toe. */
val MIGRATIE_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `backuptaak` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`serieMapNaam` TEXT NOT NULL, " +
                "`bestandsnaam` TEXT NOT NULL, " +
                "`bronUri` TEXT NOT NULL, " +
                "`submap` TEXT, " +
                "`aangemaaktOp` INTEGER NOT NULL, " +
                "`pogingen` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_backuptaak_bronUri` " +
                "ON `backuptaak` (`bronUri`)",
        )
    }
}

/** Alle migraties op volgorde; de databasebouwer krijgt deze mee. */
val ALLE_MIGRATIES = arrayOf(MIGRATIE_1_2)
