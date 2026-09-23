import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { EmptyState } from '@/src/components/EmptyState';
import { Mascot } from '@/src/components/Mascot';
import { getAiAssistantEnabled } from '@/src/db/settings';
import { isNativeAiAvailable, prepareNativeAi } from '@/src/native/aiModule';
import { runTurn } from '@/src/ai/orchestrator';
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
  const listRef = useRef<FlatList<ChatMessage>>(null);

  useEffect(() => {
    (async () => {
      if (!isNativeAiAvailable()) {
        setPhase('unavailable');
        return;
      }
      const enabled = await getAiAssistantEnabled();
      if (!enabled) {
        setPhase('disabled');
        return;
      }
      setPhase('preparing');
      const ready = await prepareNativeAi();
      setPhase(ready ? 'ready' : 'prepareFailed');
    })();
  }, []);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || sending) return;
    setInput('');
    setMessages((prev) => [...prev, { id: `u-${Date.now()}`, role: 'user', text }]);
    setSending(true);
    try {
      const result = await runTurn(text, historyRef.current);
      historyRef.current = result.history;
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
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
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
