// Warm, cheerful, slightly vintage take on a light Material palette — solid/opaque fills
// throughout (no glassy or washed-out tints), like an old apothecary label rather than a
// clinical app.
export const colors = {
  background: '#F6EFE2',
  card: '#FFFBF3',
  border: '#E6D8C0',
  text: '#3B2E22',
  textMuted: '#8A7860',
  primary: '#D97742',
  primaryMuted: '#F5DFC8',
  success: '#5A8C4E',
  successMuted: '#E3EDD8',
  warning: '#CC9A2E',
  warningMuted: '#F6E7C0',
  danger: '#B84B37',
  dangerMuted: '#F3DBD2',
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
};

export const radii = {
  sm: 8,
  md: 12,
  lg: 16,
};

// Palette for the Thyme mascot only — kept separate from the functional `colors` above so the
// character's look can change without touching status/UI colors used everywhere else. Tuned to
// sit in the same warm/vintage family as the main palette (leaf ~ success, pot ~ primary).
export const mascotColors = {
  leafLight: '#9AC77E',
  leaf: '#6B9E52',
  leafDark: '#4A7A3B',
  pot: '#D97742',
  potDark: '#B85E30',
  potRim: '#E89760',
  blush: '#F0A488',
  face: '#3B2E22',
};
