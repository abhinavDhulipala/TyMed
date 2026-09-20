import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { listMedications } from '@/src/db/medications';
import type { Medication } from '@/src/types';

export function useMedications() {
  const [medications, setMedications] = useState<Medication[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const rows = await listMedications();
    setMedications(rows);
    setLoading(false);
  }, []);

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  return { medications, loading, refresh };
}
