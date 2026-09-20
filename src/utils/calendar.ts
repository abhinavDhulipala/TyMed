import { todayDateString } from './date';

export interface CalendarDay {
  date: Date;
  dateStr: string; // "YYYY-MM-DD"
  day: number;
  inCurrentMonth: boolean;
  isToday: boolean;
  isFuture: boolean;
}

export const WEEKDAY_LABELS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

function makeCell(date: Date, inCurrentMonth: boolean, todayStr: string): CalendarDay {
  const dateStr = todayDateString(date);
  return {
    date,
    dateStr,
    day: date.getDate(),
    inCurrentMonth,
    isToday: dateStr === todayStr,
    isFuture: dateStr > todayStr,
  };
}

/** Builds a full-weeks grid (padded with adjacent months) for the given month. `month` is 0-indexed. */
export function buildMonthGrid(year: number, month: number): CalendarDay[] {
  const todayStr = todayDateString();
  const startWeekday = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const cells: CalendarDay[] = [];

  for (let i = startWeekday - 1; i >= 0; i--) {
    // Date() rolls negative day-of-month back into the previous month for us.
    cells.push(makeCell(new Date(year, month, -i), false, todayStr));
  }
  for (let day = 1; day <= daysInMonth; day++) {
    cells.push(makeCell(new Date(year, month, day), true, todayStr));
  }
  while (cells.length % 7 !== 0) {
    const last = cells[cells.length - 1].date;
    cells.push(makeCell(new Date(last.getFullYear(), last.getMonth(), last.getDate() + 1), false, todayStr));
  }

  return cells;
}

export function monthLabel(year: number, month: number): string {
  return new Date(year, month, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
}
