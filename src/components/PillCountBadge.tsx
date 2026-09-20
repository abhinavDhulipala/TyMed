import { StyleSheet, Text, View } from 'react-native';
import { colors, radii, spacing } from '@/src/theme';

interface Props {
  pillsRemaining: number | null;
  refillThreshold: number | null;
}

export function PillCountBadge({ pillsRemaining, refillThreshold }: Props) {
  if (pillsRemaining === null) return null;

  const isLow = refillThreshold !== null && pillsRemaining <= refillThreshold;

  return (
    <View style={[styles.badge, isLow ? styles.badgeLow : styles.badgeNormal]}>
      <Text style={[styles.text, isLow ? styles.textLow : styles.textNormal]}>
        {isLow ? `Refill soon · ${pillsRemaining} left` : `${pillsRemaining} left`}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
    borderRadius: radii.sm,
    alignSelf: 'flex-start',
  },
  badgeNormal: {
    backgroundColor: colors.primaryMuted,
  },
  badgeLow: {
    backgroundColor: colors.warningMuted,
  },
  text: {
    fontSize: 12,
    fontWeight: '600',
  },
  textNormal: {
    color: colors.primary,
  },
  textLow: {
    color: colors.warning,
  },
});
