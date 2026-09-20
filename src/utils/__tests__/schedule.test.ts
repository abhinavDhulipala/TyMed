import { isScheduleActiveOnWeekday } from '../schedule';

describe('isScheduleActiveOnWeekday', () => {
  it('treats null daysOfWeek as every day', () => {
    for (let day = 0; day <= 6; day++) {
      expect(isScheduleActiveOnWeekday(null, day)).toBe(true);
    }
  });

  it('matches only the listed weekdays', () => {
    const mwf = [1, 3, 5];
    expect(isScheduleActiveOnWeekday(mwf, 1)).toBe(true);
    expect(isScheduleActiveOnWeekday(mwf, 3)).toBe(true);
    expect(isScheduleActiveOnWeekday(mwf, 5)).toBe(true);
    expect(isScheduleActiveOnWeekday(mwf, 0)).toBe(false);
    expect(isScheduleActiveOnWeekday(mwf, 2)).toBe(false);
    expect(isScheduleActiveOnWeekday(mwf, 6)).toBe(false);
  });

  it('treats an empty array as never active', () => {
    expect(isScheduleActiveOnWeekday([], 0)).toBe(false);
  });
});
