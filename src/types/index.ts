export type DoseStatus = 'pending' | 'taken' | 'skipped';

export interface Medication {
  id: number;
  name: string;
  dosage: string | null;
  form: string | null;
  notes: string | null;
  pillsRemaining: number | null;
  refillThreshold: number | null;
  createdAt: string;
}

export interface MedicationInput {
  name: string;
  dosage: string | null;
  form: string | null;
  notes: string | null;
  pillsRemaining: number | null;
  refillThreshold: number | null;
}

export interface Schedule {
  id: number;
  medicationId: number;
  timeOfDay: string; // "HH:MM", 24h
  enabled: boolean;
  notificationIds: string[];
}

export interface IntakeLog {
  id: number;
  medicationId: number;
  scheduleId: number | null;
  scheduledDate: string; // "YYYY-MM-DD"
  scheduledTime: string; // "HH:MM"
  status: DoseStatus;
  takenAt: string | null;
}

export interface DoseWithMedication extends IntakeLog {
  medicationName: string;
  dosage: string | null;
}
