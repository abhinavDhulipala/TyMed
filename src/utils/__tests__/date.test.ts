import { formatDateLabel, formatTime, getTimeFormatPreference, setTimeFormatPreference, todayDateString } from '../date';

describe('todayDateString', () => {
  it('formats a given date as YYYY-MM-DD', () => {
    expect(todayDateString(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(todayDateString(new Date(2026, 11, 31))).toBe('2026-12-31');
  });

  it('pads single-digit months and days', () => {
    expect(todayDateString(new Date(2026, 2, 4))).toBe('2026-03-04');
  });
});

describe('formatTime', () => {
  it('formats midnight and noon correctly', () => {
    expect(formatTime('00:00')).toBe('12:00 AM');
    expect(formatTime('12:00')).toBe('12:00 PM');
  });

  it('formats morning and evening times with AM/PM', () => {
    expect(formatTime('08:05')).toBe('8:05 AM');
    expect(formatTime('23:45')).toBe('11:45 PM');
  });

  it('switches to 24-hour format when the preference is enabled', () => {
    expect(getTimeFormatPreference()).toBe(false);
    setTimeFormatPreference(true);
    try {
      expect(formatTime('00:00')).toBe('00:00');
      expect(formatTime('08:05')).toBe('08:05');
      expect(formatTime('23:45')).toBe('23:45');
      expect(getTimeFormatPreference()).toBe(true);
    } finally {
      setTimeFormatPreference(false);
    }
  });
});

describe('formatDateLabel', () => {
  it('labels today and yesterday specially', () => {
    const today = todayDateString();
    const yesterdayDate = new Date();
    yesterdayDate.setDate(yesterdayDate.getDate() - 1);
    const yesterday = todayDateString(yesterdayDate);

    expect(formatDateLabel(today)).toBe('Today');
    expect(formatDateLabel(yesterday)).toBe('Yesterday');
  });

  it('returns the raw date string for anything older', () => {
    expect(formatDateLabel('2020-01-01')).toBe('2020-01-01');
  });
});
