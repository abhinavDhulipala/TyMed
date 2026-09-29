package com.tymed.app.data.repository

import com.tymed.app.data.dao.MedicationDao
import com.tymed.app.data.entity.Medication
import java.time.Instant

data class MedicationInput(
    val name: String,
    val dosage: String?,
    val form: String?,
    val notes: String?,
    val pillsRemaining: Int?,
    val refillThreshold: Int?,
)

data class DuplicateCheck(
    /** A medication with the same name, dosage, and form already exists (blocking). */
    val exact: Medication?,
    /** Same name, but dosage and/or form differ — likely a typo rather than an intentional
     * second medication (warn, don't block). */
    val partial: List<Medication>,
)

class MedicationRepository(private val dao: MedicationDao) {
    suspend fun listMedications(): List<Medication> = dao.getAll()

    suspend fun getMedication(id: Long): Medication? = dao.getById(id)

    suspend fun createMedication(input: MedicationInput): Long =
        dao.insert(
            Medication(
                name = input.name,
                dosage = input.dosage,
                form = input.form,
                notes = input.notes,
                pillsRemaining = input.pillsRemaining,
                refillThreshold = input.refillThreshold,
                createdAt = Instant.now().toString(),
            ),
        )

    suspend fun updateMedication(id: Long, input: MedicationInput) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                name = input.name,
                dosage = input.dosage,
                form = input.form,
                notes = input.notes,
                pillsRemaining = input.pillsRemaining,
                refillThreshold = input.refillThreshold,
            ),
        )
    }

    suspend fun deleteMedication(id: Long) = dao.deleteById(id)

    suspend fun decrementPillCount(medicationId: Long) = dao.decrementPillCount(medicationId)

    suspend fun incrementPillCount(medicationId: Long) = dao.incrementPillCount(medicationId)

    /** Checks for existing medications that collide with the given name/dosage/form, excluding
     * [excludeId] (the medication being edited, if any). */
    suspend fun findDuplicateMedication(
        name: String,
        dosage: String?,
        form: String?,
        excludeId: Long? = null,
    ): DuplicateCheck {
        val all = dao.getAll()
        val candidates = all.filter { it.id != excludeId && normalize(it.name) == normalize(name) }
        val exact = candidates.find { normalize(it.dosage) == normalize(dosage) && normalize(it.form) == normalize(form) }
        val partial = candidates.filter { it.id != exact?.id }
        return DuplicateCheck(exact, partial)
    }

    private fun normalize(value: String?): String = (value ?: "").trim().lowercase()
}
