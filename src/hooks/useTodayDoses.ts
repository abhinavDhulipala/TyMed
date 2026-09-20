import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { getTodayDoses } from '@/src/db/logs';
import type { DoseWithMedication } from '@/src/types';

export function useTodayDoses() {
  const [doses, setDoses] = useState<DoseWithMedication[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const rows = await getTodayDoses();
    setDoses(rows);
    setLoading(false);
  }, []);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  return { doses, loading, refresh };
}
