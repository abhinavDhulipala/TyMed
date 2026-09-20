import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { getDailyAdherence, type DailyAdherence } from '@/src/db/logs';
import { buildMonthGrid } from '@/src/utils/calendar';

export function useAdherenceCalendar(year: number, month: number) {
  const [adherence, setAdherence] = useState<Record<string, DailyAdherence>>({});
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const cells = buildMonthGrid(year, month);
    const start = cells[0].dateStr;
    const end = cells[cells.length - 1].dateStr;
    const data = await getDailyAdherence(start, end);
    setAdherence(data);
    setLoading(false);
  }, [year, month]);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  return { adherence, loading, refresh };
}
