export type ParsedTurn =
  | { kind: 'tool'; name: string; arguments: Record<string, unknown> }
  | { kind: 'reply'; text: string }
  | { kind: 'unparseable'; raw: string };

function stripCodeFences(text: string): string {
  const trimmed = text.trim();
  const fenced = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
  return fenced ? fenced[1].trim() : trimmed;
}

/** AICore has no structured-output guarantee, so the model sometimes wraps its JSON in prose
 * ("Sure! {...}") instead of returning pure JSON — grab the outermost {...} block rather than
 * requiring the whole response to parse as-is. */
function extractJsonObject(text: string): string | null {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start === -1 || end === -1 || end <= start) return null;
  return text.slice(start, end + 1);
}

/** Defensively parses one model turn into a tool call, a direct reply, or "couldn't make sense
 * of this" — never throws. */
export function parseTurn(raw: string): ParsedTurn {
  const stripped = stripCodeFences(raw);
  const candidate = extractJsonObject(stripped) ?? stripped;

  let parsed: unknown;
  try {
    parsed = JSON.parse(candidate);
  } catch {
    return { kind: 'unparseable', raw };
  }

  if (typeof parsed !== 'object' || parsed === null) {
    return { kind: 'unparseable', raw };
  }
  const obj = parsed as Record<string, unknown>;

  if (typeof obj.reply === 'string') {
    return { kind: 'reply', text: obj.reply };
  }
  if (typeof obj.tool === 'string') {
    const args = isPlainObject(obj.arguments) ? obj.arguments : {};
    return { kind: 'tool', name: obj.tool, arguments: args };
  }

  return { kind: 'unparseable', raw };
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
