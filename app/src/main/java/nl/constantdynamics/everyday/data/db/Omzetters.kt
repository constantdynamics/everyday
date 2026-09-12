package nl.constantdynamics.everyday.data.db

import androidx.room.TypeConverter
import nl.constantdynamics.everyday.kern.Bron
import nl.constantdynamics.everyday.kern.LensRichting
import java.time.Instant
import java.time.LocalDate

class Omzetters {

    @TypeConverter
    fun momentNaarMillis(waarde: Instant?): Long? = waarde?.toEpochMilli()

    @TypeConverter
    fun millisNaarMoment(waarde: Long?): Instant? = waarde?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun datumNaarTekst(waarde: LocalDate?): String? = waarde?.toString()

    @TypeConverter
    fun tekstNaarDatum(waarde: String?): LocalDate? = waarde?.let(LocalDate::parse)

    @TypeConverter
    fun lensNaarTekst(waarde: LensRichting?): String? = waarde?.name

    @TypeConverter
    fun tekstNaarLens(waarde: String?): LensRichting? = waarde?.let(LensRichting::valueOf)

    @TypeConverter
    fun bronNaarTekst(waarde: Bron?): String? = waarde?.name

    @TypeConverter
    fun tekstNaarBron(waarde: String?): Bron? = waarde?.let(Bron::valueOf)
}
