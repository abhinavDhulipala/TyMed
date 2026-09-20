import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { getDosesForDate } from '@/src/db/logs';
import type { DoseWithMedication } from '@/src/types';

export function useDayDoses(dateStr: string) {
  const [doses, setDoses] = useState<DoseWithMedication[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const rows = await getDosesForDate(dateStr);
    setDoses(rows);
    setLoading(false);
  }, [dateStr]);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  return { doses, loading, refresh };
}
