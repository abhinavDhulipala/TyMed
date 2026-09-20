import { getDb } from './client';
import type { Medication, MedicationInput } from '@/src/types';

interface MedicationRow {
  id: number;
  name: string;
  dosage: string | null;
  form: string | null;
  notes: string | null;
  pills_remaining: number | null;
  refill_threshold: number | null;
  created_at: string;
}

function mapMedication(row: MedicationRow): Medication {
  return {
    id: row.id,
    name: row.name,
    dosage: row.dosage,
    form: row.form,
    notes: row.notes,
    pillsRemaining: row.pills_remaining,
    refillThreshold: row.refill_threshold,
    createdAt: row.created_at,
  };
}

export async function listMedications(): Promise<Medication[]> {
  const db = await getDb();
  const rows = await db.getAllAsync<MedicationRow>(
    'SELECT * FROM medications ORDER BY name COLLATE NOCASE'
  );
  return rows.map(mapMedication);
}

export async function getMedication(id: number): Promise<Medication | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<MedicationRow>('SELECT * FROM medications WHERE id = ?', id);
  return row ? mapMedication(row) : null;
}

export async function createMedication(input: MedicationInput): Promise<number> {
  const db = await getDb();
  const result = await db.runAsync(
    `INSERT INTO medications (name, dosage, form, notes, pills_remaining, refill_threshold, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
    input.name,
    input.dosage,
    input.form,
    input.notes,
    input.pillsRemaining,
    input.refillThreshold,
    new Date().toISOString()
  );
  return result.lastInsertRowId;
}

export async function updateMedication(id: number, input: MedicationInput): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    `UPDATE medications
     SET name = ?, dosage = ?, form = ?, notes = ?, pills_remaining = ?, refill_threshold = ?
     WHERE id = ?`,
    input.name,
    input.dosage,
    input.form,
    input.notes,
    input.pillsRemaining,
    input.refillThreshold,
    id
  );
}

export async function deleteMedication(id: number): Promise<void> {
  const db = await getDb();
  await db.runAsync('DELETE FROM medications WHERE id = ?', id);
}

export async function decrementPillCount(medicationId: number): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    `UPDATE medications SET pills_remaining = pills_remaining - 1
     WHERE id = ? AND pills_remaining IS NOT NULL AND pills_remaining > 0`,
    medicationId
  );
}

export async function incrementPillCount(medicationId: number): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    `UPDATE medications SET pills_remaining = pills_remaining + 1
     WHERE id = ? AND pills_remaining IS NOT NULL`,
    medicationId
  );
}
