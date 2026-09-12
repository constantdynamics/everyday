package nl.constantdynamics.everyday.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SerieEntiteit::class, FotoEntiteit::class, DagKeuzeEntiteit::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Omzetters::class)
abstract class EverydayDatabase : RoomDatabase() {
    abstract fun serieDao(): SerieDao
    abstract fun fotoDao(): FotoDao
    abstract fun dagKeuzeDao(): DagKeuzeDao
}
