import type { ComponentType } from 'react';

// Guarded require (not a static import), same defensive pattern used for the other native
// modules in this app: a static import would throw at module-evaluation time — and take the
// whole app down with it — before a dev client rebuild has linked the native SDK.
type SentryModule = typeof import('@sentry/react-native');

let sentryModule: SentryModule | null = null;

try {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  sentryModule = require('@sentry/react-native') as SentryModule;
} catch (error) {
  console.warn('[sentry] native module unavailable, crash reporting disabled:', error);
}

/** Call once, as early as possible (module scope of the root layout). No-ops without a DSN. */
export function initSentry(): void {
  const dsn = process.env.EXPO_PUBLIC_SENTRY_DSN;
  if (!sentryModule || !dsn) return;
  try {
    sentryModule.init({
      dsn,
      // Medication names/doses/schedules never leave the device — see legal/PRIVACY_POLICY.md.
      // Crash reports are diagnostic only (stack traces, device info), not app content.
      sendDefaultPii: false,
      tracesSampleRate: 0,
    });
  } catch (error) {
    console.warn('[sentry] failed to initialize:', error);
  }
}

/** Wraps the (prop-less) root layout component for automatic native crash capture + navigation
 * breadcrumbs. Not generic — Sentry's `wrap` has a fixed signature, and this is only ever
 * used once, for the root component. */
export function wrapRootComponent(Component: ComponentType<Record<string, never>>): ComponentType<Record<string, never>> {
  if (!sentryModule) return Component;
  try {
    return sentryModule.wrap(Component);
  } catch {
    return Component;
  }
}

/** Reports a caught (non-fatal) error. Never throws — a reporting failure must not crash the app. */
export function captureException(error: unknown): void {
  try {
    sentryModule?.captureException(error);
  } catch {
    // Swallow.
  }
}
