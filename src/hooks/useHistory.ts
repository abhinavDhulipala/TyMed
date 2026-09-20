import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { getHistory } from '@/src/db/logs';
import type { DoseWithMedication } from '@/src/types';

export function useHistory() {
  const [history, setHistory] = useState<DoseWithMedication[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const rows = await getHistory();
    setHistory(rows);
    setLoading(false);
  }, []);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  return { history, loading, refresh };
}
