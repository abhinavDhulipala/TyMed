import { useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import DateTimePicker, { type DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { colors, radii, spacing } from '@/src/theme';
import { formatTime } from '@/src/utils/date';

interface Props {
  value: string; // "HH:MM"
  onChange: (value: string) => void;
  onRemove: () => void;
}

function timeStringToDate(value: string): Date {
  const [h, m] = value.split(':').map(Number);
  const date = new Date();
  date.setHours(h, m, 0, 0);
  return date;
}

function dateToTimeString(date: Date): string {
  const h = String(date.getHours()).padStart(2, '0');
  const m = String(date.getMinutes()).padStart(2, '0');
  return `${h}:${m}`;
}

export function TimeOfDayRow({ value, onChange, onRemove }: Props) {
  const [showPicker, setShowPicker] = useState(false);

  const handleChange = (event: DateTimePickerEvent, selected?: Date) => {
    setShowPicker(Platform.OS === 'ios');
    if (event.type === 'set' && selected) {
      onChange(dateToTimeString(selected));
    }
  };

  return (
    <View style={styles.row}>
      <Pressable style={styles.timeButton} onPress={() => setShowPicker(true)}>
        <Text style={styles.timeText}>{formatTime(value)}</Text>
      </Pressable>
      <Pressable style={styles.removeButton} onPress={onRemove}>
        <Text style={styles.removeText}>Remove</Text>
      </Pressable>
      {showPicker ? (
        <DateTimePicker value={timeStringToDate(value)} mode="time" display="default" onChange={handleChange} />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    marginBottom: spacing.sm,
  },
  timeButton: {
    flex: 1,
    backgroundColor: colors.primaryMuted,
    borderRadius: radii.sm,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
  },
  timeText: {
    color: colors.primary,
    fontWeight: '700',
    fontSize: 16,
  },
  removeButton: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.sm,
  },
  removeText: {
    color: colors.danger,
    fontWeight: '600',
  },
});
