import { isScheduleActiveOn, recurrenceRuleEquals, type RecurrenceRule } from '../schedule';

function rule(overrides: Partial<RecurrenceRule>): RecurrenceRule {
  return { recurrenceType: 'daily', daysOfWeek: null, startDate: null, endDate: null, ...overrides };
}

describe('isScheduleActiveOn — daily', () => {
  it('is active on every date', () => {
    expect(isScheduleActiveOn(rule({ recurrenceType: 'daily' }), '2026-09-20')).toBe(true);
    expect(isScheduleActiveOn(rule({ recurrenceType: 'daily' }), '2027-01-01')).toBe(true);
  });
});

describe('isScheduleActiveOn — weekly', () => {
  // 2026-09-20 is a Sunday.
  const mwf = rule({ recurrenceType: 'weekly', daysOfWeek: [1, 3, 5] });

  it('matches only the selected weekdays', () => {
    expect(isScheduleActiveOn(mwf, '2026-09-21')).toBe(true); // Monday
    expect(isScheduleActiveOn(mwf, '2026-09-23')).toBe(true); // Wednesday
    expect(isScheduleActiveOn(mwf, '2026-09-25')).toBe(true); // Friday
    expect(isScheduleActiveOn(mwf, '2026-09-20')).toBe(false); // Sunday
    expect(isScheduleActiveOn(mwf, '2026-09-22')).toBe(false); // Tuesday
    expect(isScheduleActiveOn(mwf, '2026-09-26')).toBe(false); // Saturday
  });

  it('is never active with no days selected', () => {
    expect(isScheduleActiveOn(rule({ recurrenceType: 'weekly', daysOfWeek: [] }), '2026-09-21')).toBe(false);
  });
});

describe('isScheduleActiveOn — monthly', () => {
  it('fires on the same day-of-month as the start date', () => {
    const r = rule({ recurrenceType: 'monthly', startDate: '2026-09-15' });
    expect(isScheduleActiveOn(r, '2026-09-15')).toBe(true);
    expect(isScheduleActiveOn(r, '2026-10-15')).toBe(true);
    expect(isScheduleActiveOn(r, '2026-09-14')).toBe(false);
    expect(isScheduleActiveOn(r, '2026-09-16')).toBe(false);
  });

  it('clamps to the last day of shorter months when anchored on the 31st', () => {
    const r = rule({ recurrenceType: 'monthly', startDate: '2026-01-31' });
    expect(isScheduleActiveOn(r, '2026-01-31')).toBe(true);
    expect(isScheduleActiveOn(r, '2026-02-28')).toBe(true); // Feb 2026 has 28 days
    expect(isScheduleActiveOn(r, '2026-03-31')).toBe(true);
    expect(isScheduleActiveOn(r, '2026-04-30')).toBe(true); // April has 30 days
  });

  it('is never active without a start date', () => {
    expect(isScheduleActiveOn(rule({ recurrenceType: 'monthly', startDate: null }), '2026-09-15')).toBe(false);
  });
});

describe('isScheduleActiveOn — start/end date bounds', () => {
  it('is inactive before the start date', () => {
    const r = rule({ recurrenceType: 'daily', startDate: '2026-09-20' });
    expect(isScheduleActiveOn(r, '2026-09-19')).toBe(false);
    expect(isScheduleActiveOn(r, '2026-09-20')).toBe(true);
  });

  it('is inactive after the end date (inclusive boundary)', () => {
    const r = rule({ recurrenceType: 'daily', endDate: '2026-09-25' });
    expect(isScheduleActiveOn(r, '2026-09-25')).toBe(true);
    expect(isScheduleActiveOn(r, '2026-09-26')).toBe(false);
  });
});

describe('recurrenceRuleEquals', () => {
  it('treats identical rules as equal', () => {
    const a = rule({ recurrenceType: 'weekly', daysOfWeek: [1, 3] });
    const b = rule({ recurrenceType: 'weekly', daysOfWeek: [1, 3] });
    expect(recurrenceRuleEquals(a, b)).toBe(true);
  });

  it('detects a changed field', () => {
    const a = rule({ recurrenceType: 'weekly', daysOfWeek: [1, 3] });
    expect(recurrenceRuleEquals(a, { ...a, daysOfWeek: [1, 4] })).toBe(false);
    expect(recurrenceRuleEquals(a, { ...a, endDate: '2026-12-01' })).toBe(false);
    expect(recurrenceRuleEquals(a, { ...a, recurrenceType: 'daily' })).toBe(false);
  });
});
