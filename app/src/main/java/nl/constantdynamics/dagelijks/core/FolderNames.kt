package nl.constantdynamics.dagelijks.core

/**
 * Turns a project name the user typed into a folder name that is valid on the
 * phone and on a Windows computer reading it over USB.
 */
object FolderNames {

    private const val MAX_LENGTH = 60
    private const val FALLBACK = "Project"

    private val forbidden = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]""")
    private val whitespace = Regex("""\s+""")
    private val windowsReserved = setOf("CON", "PRN", "AUX", "NUL") +
        (1..9).map { "COM$it" } + (1..9).map { "LPT$it" }

    fun safeFolderName(displayName: String): String {
        var name = forbidden.replace(displayName, " ")
        name = whitespace.replace(name, " ").trim()
        // Windows refuses names that start or end with a dot or a space.
        name = name.trim('.', ' ')
        if (name.length > MAX_LENGTH) name = name.take(MAX_LENGTH).trim('.', ' ')
        if (name.isEmpty()) name = FALLBACK
        if (name.substringBefore('.').uppercase() in windowsReserved) name = "$name map"
        return name
    }

    /**
     * Adds " 2", " 3", ... until the name is free. Comparison ignores case because
     * phone storage and Windows both do.
     */
    fun uniqueFolderName(wanted: String, existing: Collection<String>): String {
        val taken = existing.map { it.lowercase() }.toSet()
        if (wanted.lowercase() !in taken) return wanted
        var number = 2
        while ("$wanted $number".lowercase() in taken) number++
        return "$wanted $number"
    }
}
