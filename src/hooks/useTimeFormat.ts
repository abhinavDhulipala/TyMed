import { useEffect, useState } from 'react';
import { getTimeFormatPreference } from '@/src/utils/date';

// The date.ts module flag is set globally (on app start, and whenever Settings saves a change),
// but a mounted screen won't re-render on that mutation by itself — this hook subscribes via a
// simple event so open pickers pick up a mid-session format change immediately.
type Listener = () => void;
const listeners = new Set<Listener>();

export function notifyTimeFormatChanged(): void {
  listeners.forEach((l) => l());
}

export function useTimeFormat(): boolean {
  const [is24Hour, setIs24Hour] = useState(getTimeFormatPreference());

  useEffect(() => {
    const listener = () => setIs24Hour(getTimeFormatPreference());
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }, []);

  return is24Hour;
}
