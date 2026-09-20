import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { getDoseById } from '@/src/db/logs';
import type { DoseWithMedication } from '@/src/types';

export function useDose(logId: number) {
  const [dose, setDose] = useState<DoseWithMedication | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const row = await getDoseById(logId);
    setDose(row);
    setLoading(false);
  }, [logId]);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  return { dose, loading, refresh };
}
