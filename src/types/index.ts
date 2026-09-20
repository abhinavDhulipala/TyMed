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

export type RecurrenceType = 'daily' | 'weekly' | 'monthly';

export interface Schedule {
  id: number;
  medicationId: number;
  timeOfDay: string; // "HH:MM", 24h
  enabled: boolean;
  notificationIds: string[];
  recurrenceType: RecurrenceType;
  /** 0=Sun..6=Sat days this dose recurs on. Only meaningful (and always non-null) when
   * recurrenceType === 'weekly'. */
  daysOfWeek: number[] | null;
  /** "YYYY-MM-DD". The day-of-month anchor for monthly recurrence; also the earliest date this
   * schedule is active on (null = no lower bound, for schedules predating this field). */
  startDate: string | null;
  /** "YYYY-MM-DD", inclusive — last date this schedule reminds on. null = no end date. */
  endDate: string | null;
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
