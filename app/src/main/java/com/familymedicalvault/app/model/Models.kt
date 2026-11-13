package com.familymedicalvault.app.model

data class PersonProfile(
    val id: String,
    val displayName: String,
    val relation: String = ""
)

enum class MedicalCategory(val folderName: String) {
    EXAMS("Εξετάσεις"),
    IMAGING("Απεικονίσεις"),
    VACCINES("Εμβόλια"),
    MEDICATIONS("Φάρμακα"),
    HOSPITALIZATIONS("Νοσηλείες"),
    REPORTS("Γνωματεύσεις");

    companion object {
        fun fromFolderName(value: String): MedicalCategory? = entries.firstOrNull { it.folderName == value }
    }
}
