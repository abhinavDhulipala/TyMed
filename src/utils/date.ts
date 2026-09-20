export function todayDateString(date: Date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

// Set once at app startup (and whenever the user changes the setting) from src/db/settings.ts —
// formatTime is called from render paths all over the app, so it reads a module-level flag
// rather than every caller threading the setting through as a prop.
let use24Hour = false;

export function setTimeFormatPreference(value: boolean): void {
  use24Hour = value;
}

export function getTimeFormatPreference(): boolean {
  return use24Hour;
}

export function formatTime(hhmm: string): string {
  const [h, m] = hhmm.split(':').map(Number);
  if (use24Hour) {
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  }
  const period = h >= 12 ? 'PM' : 'AM';
  const hour12 = h % 12 === 0 ? 12 : h % 12;
  return `${hour12}:${String(m).padStart(2, '0')} ${period}`;
}

function parseDateStr(dateStr: string): Date {
  const [y, m, d] = dateStr.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function formatDateLabel(dateStr: string): string {
  const today = todayDateString();
  const yesterdayDate = new Date();
  yesterdayDate.setDate(yesterdayDate.getDate() - 1);
  const yesterday = todayDateString(yesterdayDate);
  if (dateStr === today) return 'Today';
  if (dateStr === yesterday) return 'Yesterday';
  return dateStr;
}

/** Full readable date, e.g. "Monday, September 15" — falls back to "Today"/"Yesterday". */
export function formatFullDateLabel(dateStr: string): string {
  const label = formatDateLabel(dateStr);
  if (label === 'Today' || label === 'Yesterday') return label;
  return parseDateStr(dateStr).toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  });
}
