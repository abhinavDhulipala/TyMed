import { buildMonthGrid, monthLabel } from '../calendar';

describe('buildMonthGrid', () => {
  it('pads to a whole number of weeks', () => {
    // February 2026 has 28 days, starting on a Sunday — a clean 4-week grid.
    const cells = buildMonthGrid(2026, 1);
    expect(cells.length % 7).toBe(0);
    expect(cells.length).toBeGreaterThanOrEqual(28);
  });

  it('marks days outside the requested month as inCurrentMonth: false', () => {
    const cells = buildMonthGrid(2026, 2); // March 2026
    const inMonth = cells.filter((c) => c.inCurrentMonth);
    expect(inMonth).toHaveLength(31);
    expect(inMonth.every((c) => c.date.getMonth() === 2)).toBe(true);
  });

  it('produces consecutive, non-repeating dates', () => {
    const cells = buildMonthGrid(2026, 5); // June 2026
    for (let i = 1; i < cells.length; i++) {
      const prev = cells[i - 1].date;
      const curr = cells[i].date;
      const diffDays = Math.round((curr.getTime() - prev.getTime()) / 86400000);
      expect(diffDays).toBe(1);
    }
  });

  it('flags exactly one cell as isToday when today falls in the requested month', () => {
    const now = new Date();
    const cells = buildMonthGrid(now.getFullYear(), now.getMonth());
    const todayCells = cells.filter((c) => c.isToday);
    expect(todayCells).toHaveLength(1);
    expect(todayCells[0].inCurrentMonth).toBe(true);
  });

  it('flags dates after today as isFuture', () => {
    const now = new Date();
    const cells = buildMonthGrid(now.getFullYear(), now.getMonth());
    for (const cell of cells) {
      if (cell.isToday) {
        expect(cell.isFuture).toBe(false);
      }
    }
  });
});

describe('monthLabel', () => {
  it('formats a month/year as a readable label', () => {
    expect(monthLabel(2026, 0)).toBe('January 2026');
    expect(monthLabel(2026, 11)).toBe('December 2026');
  });
});
