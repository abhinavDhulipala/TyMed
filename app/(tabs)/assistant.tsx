import { useCallback, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useFocusEffect } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { EmptyState } from '@/src/components/EmptyState';
import { Mascot } from '@/src/components/Mascot';
import { getAiAssistantEnabled } from '@/src/db/settings';
import { isNativeAiAvailable, prepareNativeAi } from '@/src/native/aiModule';
import { runTurn, type PendingAction } from '@/src/ai/orchestrator';
import type { HistoryEntry } from '@/src/ai/prompt';
import { colors, radii, spacing } from '@/src/theme';

type Phase = 'checking' | 'disabled' | 'unavailable' | 'preparing' | 'prepareFailed' | 'ready';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
}

export default function AssistantScreen() {
  const [phase, setPhase] = useState<Phase>('checking');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const historyRef = useRef<HistoryEntry[]>([]);
  const pendingRef = useRef<PendingAction | null>(null);
  const listRef = useRef<FlatList<ChatMessage>>(null);
  const headerHeight = useHeaderHeight();

  const phaseRef = useRef<Phase>('checking');

  const updatePhase = useCallback((next: Phase) => {
    phaseRef.current = next;
    setPhase(next);
  }, []);

  // Tabs stay mounted, so this re-checks on every focus — otherwise toggling the assistant on in
  // Settings after this tab was first opened leaves it stuck on "off" until an app restart.
  useFocusEffect(
    useCallback(() => {
      (async () => {
        if (!isNativeAiAvailable()) {
          updatePhase('unavailable');
          return;
        }
        const enabled = await getAiAssistantEnabled();
        if (!enabled) {
          updatePhase('disabled');
          return;
        }
        // Already set up (or mid-setup) from an earlier visit — don't re-run prepare on every focus.
        if (phaseRef.current === 'ready' || phaseRef.current === 'preparing') return;
        updatePhase('preparing');
        const ready = await prepareNativeAi();
        updatePhase(ready ? 'ready' : 'prepareFailed');
      })();
    }, [updatePhase])
  );

  const handleSend = async () => {
    const text = input.trim();
    if (!text || sending) return;
    setInput('');
    setMessages((prev) => [...prev, { id: `u-${Date.now()}`, role: 'user', text }]);
    setSending(true);
    try {
      const result = await runTurn(text, historyRef.current, pendingRef.current);
      historyRef.current = result.history;
      pendingRef.current = result.pending;
      setMessages((prev) => [...prev, { id: `a-${Date.now()}`, role: 'assistant', text: result.reply }]);
    } catch {
      setMessages((prev) => [
        ...prev,
        { id: `a-${Date.now()}`, role: 'assistant', text: "Something went wrong on my end — let's try that again." },
      ]);
    } finally {
      setSending(false);
    }
  };

  if (phase === 'checking') {
    return (
      <View style={styles.centered}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  if (phase === 'unavailable') {
    return (
      <View style={styles.centered}>
        <EmptyState
          title="Not available on this device"
          subtitle="The AI assistant needs Android's on-device Gemini Nano, which isn't supported here."
        />
      </View>
    );
  }

  if (phase === 'disabled') {
    return (
      <View style={styles.centered}>
        <EmptyState
          title="AI Assistant is off"
          subtitle="Turn it on from Settings to chat about your medications."
        />
      </View>
    );
  }

  if (phase === 'preparing') {
    return (
      <View style={styles.centered}>
        <Mascot size={56} />
        <Text style={styles.preparingText}>Preparing on-device AI…</Text>
      </View>
    );
  }

  if (phase === 'prepareFailed') {
    return (
      <View style={styles.centered}>
        <EmptyState
          title="Couldn't set up the assistant"
          subtitle="This device may not support Gemini Nano, or the on-device model couldn't be downloaded."
        />
      </View>
    );
  }

  return (
    // 'padding' on Android too: the app draws edge-to-edge (enforced from Android 15), where the
    // window no longer resizes for the keyboard. KeyboardAvoidingView measures itself relative to
    // this screen but the keyboard in window coordinates, so it needs the header (+ status bar)
    // height as an offset — without it the input row still ends up partly behind the keyboard.
    <KeyboardAvoidingView style={styles.container} behavior="padding" keyboardVerticalOffset={headerHeight}>
      <FlatList
        ref={listRef}
        style={styles.list}
        contentContainerStyle={styles.listContent}
        data={messages}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <View style={[styles.bubble, item.role === 'user' ? styles.bubbleUser : styles.bubbleAssistant]}>
            <Text style={item.role === 'user' ? styles.bubbleTextUser : styles.bubbleText}>{item.text}</Text>
          </View>
        )}
        ListEmptyComponent={
          <EmptyState
            title="Ask about your medications"
            subtitle='Try "what do I have left today" or "add ibuprofen 200mg at 8am".'
          />
        }
        onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: true })}
      />
      <View style={styles.inputRow}>
        <TextInput
          style={styles.input}
          value={input}
          onChangeText={setInput}
          placeholder="Message TyMed…"
          placeholderTextColor={colors.textMuted}
          editable={!sending}
          onSubmitEditing={handleSend}
          returnKeyType="send"
        />
        <Pressable style={styles.sendButton} onPress={handleSend} disabled={sending || !input.trim()}>
          {sending ? <ActivityIndicator color="#FFFFFF" size="small" /> : <Text style={styles.sendButtonText}>Send</Text>}
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.lg,
    gap: spacing.sm,
  },
  preparingText: {
    fontSize: 14,
    color: colors.textMuted,
  },
  list: {
    flex: 1,
  },
  listContent: {
    padding: spacing.md,
    flexGrow: 1,
  },
  bubble: {
    borderRadius: radii.md,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    marginBottom: spacing.sm,
    maxWidth: '85%',
  },
  bubbleUser: {
    backgroundColor: colors.primary,
    alignSelf: 'flex-end',
  },
  bubbleAssistant: {
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    alignSelf: 'flex-start',
  },
  bubbleText: {
    color: colors.text,
    fontSize: 15,
  },
  bubbleTextUser: {
    color: '#FFFFFF',
    fontSize: 15,
  },
  inputRow: {
    flexDirection: 'row',
    gap: spacing.sm,
    padding: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    backgroundColor: colors.card,
  },
  input: {
    flex: 1,
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    fontSize: 16,
    color: colors.text,
  },
  sendButton: {
    backgroundColor: colors.primary,
    borderRadius: radii.sm,
    paddingHorizontal: spacing.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendButtonText: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
});
