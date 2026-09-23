import { useEffect, useState } from 'react';
import { Alert, Linking, Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import Constants from 'expo-constants';
import {
  DEFAULT_FOLLOW_UP_MINUTES,
  getAiAssistantEnabled,
  getFollowUpMinutes,
  getUse24HourFormat,
  setAiAssistantEnabled,
  setFollowUpMinutes,
  setUse24HourFormat,
} from '@/src/db/settings';
import { setNativeFollowUpMinutes } from '@/src/native/alarmModule';
import { isNativeAiAvailable } from '@/src/native/aiModule';
import { Mascot } from '@/src/components/Mascot';
import { notifyTimeFormatChanged } from '@/src/hooks/useTimeFormat';
import { colors, radii, spacing } from '@/src/theme';
import { setTimeFormatPreference } from '@/src/utils/date';

const LEGAL_URL = 'https://claude.ai/artifact/3dDgZ3qApmgKxCY8vDHzLS';

export default function SettingsScreen() {
  const [minutesInput, setMinutesInput] = useState(String(DEFAULT_FOLLOW_UP_MINUTES));
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [is24Hour, setIs24Hour] = useState(false);
  const [aiEnabled, setAiEnabled] = useState(false);
  const aiAvailable = isNativeAiAvailable();

  useEffect(() => {
    (async () => {
      const [minutes, use24Hour, aiAssistantEnabled] = await Promise.all([
        getFollowUpMinutes(),
        getUse24HourFormat(),
        getAiAssistantEnabled(),
      ]);
      setMinutesInput(String(minutes));
      setIs24Hour(use24Hour);
      setAiEnabled(aiAssistantEnabled);
      setLoading(false);
    })();
  }, []);

  const handleSave = async () => {
    const minutes = Number(minutesInput);
    if (!Number.isFinite(minutes) || minutes <= 0) {
      Alert.alert('Invalid interval', 'Enter a whole number of minutes greater than 0.');
      return;
    }
    setSaving(true);
    await setFollowUpMinutes(minutes);
    setNativeFollowUpMinutes(minutes);
    setSaving(false);
    Alert.alert('Saved', `Follow-up alarms will now repeat every ${minutes} minute${minutes === 1 ? '' : 's'}.`);
  };

  const handleToggle24Hour = async (value: boolean) => {
    setIs24Hour(value);
    setTimeFormatPreference(value);
    notifyTimeFormatChanged();
    await setUse24HourFormat(value);
  };

  const handleToggleAiAssistant = async (value: boolean) => {
    setAiEnabled(value);
    await setAiAssistantEnabled(value);
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.hero}>
        <Mascot size={72} />
        <Text style={styles.heroTitle}>TyMed</Text>
      </View>

      <Text style={styles.sectionTitle}>Display</Text>
      <View style={styles.card}>
        <View style={styles.switchRow}>
          <View style={styles.switchLabelGroup}>
            <Text style={styles.label}>24-hour time</Text>
            <Text style={styles.help}>Show times like 14:30 instead of 2:30 PM.</Text>
          </View>
          <Switch
            value={is24Hour}
            onValueChange={handleToggle24Hour}
            disabled={loading}
            trackColor={{ false: colors.border, true: colors.primary }}
            thumbColor="#FFFFFF"
          />
        </View>
      </View>

      <Text style={styles.sectionTitle}>AI Assistant</Text>
      <View style={styles.card}>
        <View style={styles.switchRow}>
          <View style={styles.switchLabelGroup}>
            <Text style={styles.label}>Chat assistant</Text>
            <Text style={styles.help}>
              {aiAvailable
                ? 'Ask about your medications, add one, or mark a dose taken by chatting — powered entirely by ' +
                  "Android's on-device Gemini Nano. Nothing leaves your device."
                : "Not available on this device — needs Android's on-device Gemini Nano (AICore)."}
            </Text>
          </View>
          <Switch
            value={aiEnabled}
            onValueChange={handleToggleAiAssistant}
            disabled={loading || !aiAvailable}
            trackColor={{ false: colors.border, true: colors.primary }}
            thumbColor="#FFFFFF"
          />
        </View>
      </View>

      <Text style={styles.sectionTitle}>Reminders</Text>
      <View style={styles.card}>
        <Text style={styles.label}>Follow-up reminder interval</Text>
        <Text style={styles.help}>
          If a dose alarm is snoozed (or ignored) instead of marked taken, it rings again after this many minutes —
          repeating until you mark the dose taken. Android only; iOS uses a fixed reminder pattern.
        </Text>
        <View style={styles.row}>
          <TextInput
            style={styles.input}
            value={minutesInput}
            onChangeText={(t) => setMinutesInput(t.replace(/[^0-9]/g, ''))}
            keyboardType="number-pad"
            editable={!loading}
          />
          <Text style={styles.unit}>minutes</Text>
        </View>
        <Pressable style={styles.button} onPress={handleSave} disabled={loading || saving}>
          <Text style={styles.buttonText}>{saving ? 'Saving…' : 'Save'}</Text>
        </Pressable>
      </View>

      <Text style={styles.sectionTitle}>About</Text>
      <View style={styles.card}>
        <Text style={styles.disclaimer}>
          TyMed is a reminder and tracking tool, not a substitute for professional medical advice. Always confirm
          medications, dosages, and timing with your doctor or pharmacist.
        </Text>
        <Pressable onPress={() => Linking.openURL(`${LEGAL_URL}#privacy`)}>
          <Text style={styles.link}>Privacy Policy</Text>
        </Pressable>
        <Pressable onPress={() => Linking.openURL(`${LEGAL_URL}#terms`)}>
          <Text style={styles.link}>Terms of Use</Text>
        </Pressable>
        <Text style={styles.version}>TyMed v{Constants.expoConfig?.version ?? '1.0.0'}</Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    padding: spacing.md,
  },
  hero: {
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  heroTitle: {
    fontSize: 20,
    fontWeight: '800',
    color: colors.text,
    marginTop: spacing.xs,
  },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.md,
  },
  switchLabelGroup: {
    flex: 1,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: colors.textMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: spacing.xs,
    marginTop: spacing.md,
  },
  card: {
    backgroundColor: colors.card,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.md,
  },
  label: {
    fontSize: 16,
    fontWeight: '700',
    color: colors.text,
    marginBottom: spacing.xs,
  },
  help: {
    fontSize: 14,
    color: colors.textMuted,
    marginBottom: spacing.md,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  input: {
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    fontSize: 16,
    color: colors.text,
    width: 100,
  },
  unit: {
    fontSize: 16,
    color: colors.textMuted,
  },
  button: {
    backgroundColor: colors.primary,
    borderRadius: radii.sm,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  buttonText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 16,
  },
  disclaimer: {
    fontSize: 13,
    color: colors.textMuted,
    marginBottom: spacing.md,
    lineHeight: 19,
  },
  link: {
    fontSize: 15,
    color: colors.primary,
    fontWeight: '600',
    marginBottom: spacing.sm,
  },
  version: {
    fontSize: 12,
    color: colors.textMuted,
    marginTop: spacing.xs,
  },
});
