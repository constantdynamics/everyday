package nl.constantdynamics.dagelijks.core

/** Which camera a project uses. The id is what ends up in project.json. */
enum class Lens(val id: String) {
    FRONT("voor"),
    BACK("achter");

    companion object {
        fun fromId(id: String?): Lens? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
    }
}
