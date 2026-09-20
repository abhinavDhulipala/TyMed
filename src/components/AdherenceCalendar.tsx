import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import Svg, { Circle } from 'react-native-svg';
import { useAdherenceCalendar } from '@/src/hooks/useAdherenceCalendar';
import { buildMonthGrid, monthLabel, type CalendarDay, WEEKDAY_LABELS } from '@/src/utils/calendar';
import { colors, radii, spacing } from '@/src/theme';

const DIAL_SIZE = 34;
const DIAL_STROKE = 3;
const DIAL_RADIUS = (DIAL_SIZE - DIAL_STROKE) / 2;
const DIAL_CIRCUMFERENCE = 2 * Math.PI * DIAL_RADIUS;

function DayDial({ percentage }: { percentage: number }) {
  const offset = DIAL_CIRCUMFERENCE * (1 - percentage);
  return (
    <Svg width={DIAL_SIZE} height={DIAL_SIZE} style={styles.dialSvg}>
      <Circle
        cx={DIAL_SIZE / 2}
        cy={DIAL_SIZE / 2}
        r={DIAL_RADIUS}
        stroke={colors.border}
        strokeWidth={DIAL_STROKE}
        fill="none"
      />
      <Circle
        cx={DIAL_SIZE / 2}
        cy={DIAL_SIZE / 2}
        r={DIAL_RADIUS}
        stroke={colors.success}
        strokeWidth={DIAL_STROKE}
        strokeLinecap="round"
        strokeDasharray={`${DIAL_CIRCUMFERENCE} ${DIAL_CIRCUMFERENCE}`}
        strokeDashoffset={offset}
        fill="none"
        rotation={-90}
        origin={`${DIAL_SIZE / 2}, ${DIAL_SIZE / 2}`}
      />
    </Svg>
  );
}

export function AdherenceCalendar() {
  const now = new Date();
  const [cursor, setCursor] = useState({ year: now.getFullYear(), month: now.getMonth() });
  const { adherence, loading } = useAdherenceCalendar(cursor.year, cursor.month);
  const router = useRouter();
  const cells = buildMonthGrid(cursor.year, cursor.month);
  const hasAnyData = Object.keys(adherence).length > 0;

  const goToMonth = (delta: number) => {
    setCursor((prev) => {
      const date = new Date(prev.year, prev.month + delta, 1);
      return { year: date.getFullYear(), month: date.getMonth() };
    });
  };

  const monthStats = cells.reduce(
    (acc, cell) => {
      if (!cell.inCurrentMonth) return acc;
      const stats = adherence[cell.dateStr];
      if (stats) {
        acc.taken += stats.taken;
        acc.resolved += stats.resolved;
      }
      return acc;
    },
    { taken: 0, resolved: 0 }
  );
  const monthPercentage = monthStats.resolved > 0 ? Math.round((monthStats.taken / monthStats.resolved) * 100) : null;

  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <Pressable style={styles.navButton} onPress={() => goToMonth(-1)} hitSlop={8}>
          <Ionicons name="chevron-back" size={18} color={colors.primary} />
        </Pressable>
        <View style={styles.headerCenter}>
          <Text style={styles.monthLabel}>{monthLabel(cursor.year, cursor.month)}</Text>
          {monthPercentage !== null ? (
            <View style={styles.monthBadge}>
              <Text style={styles.monthBadgeText}>{monthPercentage}% taken</Text>
            </View>
          ) : null}
        </View>
        <Pressable style={styles.navButton} onPress={() => goToMonth(1)} hitSlop={8}>
          <Ionicons name="chevron-forward" size={18} color={colors.primary} />
        </Pressable>
      </View>

      <View style={styles.weekRow}>
        {WEEKDAY_LABELS.map((label, i) => (
          <Text key={i} style={styles.weekdayLabel}>
            {label}
          </Text>
        ))}
      </View>

      <View style={styles.grid}>
        {cells.map((cell) => (
          <DayCell key={cell.dateStr} cell={cell} stats={adherence[cell.dateStr]} onPress={() => router.push(`/day/${cell.dateStr}`)} />
        ))}
      </View>

      {!loading && !hasAnyData ? (
        <Text style={styles.emptyHint}>Days you mark taken or skipped will show up here.</Text>
      ) : null}
    </View>
  );
}

function DayCell({
  cell,
  stats,
  onPress,
}: {
  cell: CalendarDay;
  stats?: { taken: number; resolved: number };
  onPress: () => void;
}) {
  const percentage = stats && stats.resolved > 0 ? stats.taken / stats.resolved : null;
  const showDial = cell.inCurrentMonth && !cell.isFuture && percentage !== null;

  return (
    <Pressable
      style={({ pressed }) => [styles.cell, pressed && styles.cellPressed]}
      onPress={onPress}
      hitSlop={2}
    >
      {cell.isToday ? <View style={styles.todayBackground} /> : null}
      {showDial ? <DayDial percentage={percentage!} /> : null}
      <Text
        style={[
          styles.dayText,
          !cell.inCurrentMonth && styles.dayTextMuted,
          cell.isToday && styles.dayTextToday,
        ]}
      >
        {cell.day}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.card,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.md,
    marginBottom: spacing.md,
    shadowColor: '#0F172A',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.06,
    shadowRadius: 12,
    elevation: 2,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.md,
  },
  navButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: colors.primaryMuted,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerCenter: {
    alignItems: 'center',
    gap: 4,
  },
  monthLabel: {
    fontSize: 17,
    fontWeight: '700',
    color: colors.text,
  },
  monthBadge: {
    backgroundColor: colors.successMuted,
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
    borderRadius: radii.sm,
  },
  monthBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: colors.success,
  },
  weekRow: {
    flexDirection: 'row',
    marginBottom: spacing.xs,
    borderBottomWidth: 1,
    borderBottomColor: colors.background,
    paddingBottom: spacing.xs,
  },
  weekdayLabel: {
    flex: 1,
    textAlign: 'center',
    fontSize: 11,
    fontWeight: '700',
    color: colors.textMuted,
    letterSpacing: 0.5,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  cell: {
    width: `${100 / 7}%`,
    aspectRatio: 1,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radii.sm,
  },
  cellPressed: {
    backgroundColor: colors.background,
  },
  // Absolutely-positioned children ignore the parent's alignItems/justifyContent in RN, so
  // centering has to be done explicitly: anchor to the cell's midpoint, then pull back by half
  // the element's own size.
  dialSvg: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    marginTop: -DIAL_SIZE / 2,
    marginLeft: -DIAL_SIZE / 2,
  },
  todayBackground: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    width: DIAL_SIZE + 6,
    height: DIAL_SIZE + 6,
    marginTop: -(DIAL_SIZE + 6) / 2,
    marginLeft: -(DIAL_SIZE + 6) / 2,
    borderRadius: (DIAL_SIZE + 6) / 2,
    backgroundColor: colors.primaryMuted,
  },
  dayText: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.text,
  },
  dayTextMuted: {
    color: colors.border,
  },
  dayTextToday: {
    color: colors.primary,
    fontWeight: '800',
  },
  emptyHint: {
    fontSize: 12,
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: spacing.sm,
  },
});
