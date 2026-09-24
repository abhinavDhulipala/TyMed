import { parseTurn } from '../parseTurn';

describe('parseTurn — tool calls', () => {
  it('parses a plain tool-call object', () => {
    const result = parseTurn('{"tool": "get_todays_doses", "arguments": {}}');
    expect(result).toEqual({ kind: 'tool', name: 'get_todays_doses', arguments: {} });
  });

  it('defaults arguments to {} when omitted', () => {
    const result = parseTurn('{"tool": "get_todays_doses"}');
    expect(result).toEqual({ kind: 'tool', name: 'get_todays_doses', arguments: {} });
  });

  it('defaults arguments to {} when arguments is not an object', () => {
    const result = parseTurn('{"tool": "get_todays_doses", "arguments": "oops"}');
    expect(result).toEqual({ kind: 'tool', name: 'get_todays_doses', arguments: {} });
  });

  it('unwraps a ```json code fence', () => {
    const result = parseTurn('```json\n{"tool": "get_todays_doses", "arguments": {}}\n```');
    expect(result).toEqual({ kind: 'tool', name: 'get_todays_doses', arguments: {} });
  });

  it('extracts the JSON object out of surrounding prose', () => {
    const result = parseTurn('Sure, let me check that. {"tool": "get_todays_doses", "arguments": {}} Just a sec.');
    expect(result).toEqual({ kind: 'tool', name: 'get_todays_doses', arguments: {} });
  });
});

describe('parseTurn — replies', () => {
  it('parses a plain reply object', () => {
    const result = parseTurn('{"reply": "You have 2 doses left today."}');
    expect(result).toEqual({ kind: 'reply', text: 'You have 2 doses left today.' });
  });
});

describe('parseTurn — unparseable', () => {
  it('rejects invalid JSON', () => {
    const raw = 'not json at all';
    expect(parseTurn(raw)).toEqual({ kind: 'unparseable', raw });
  });

  it('rejects a JSON array', () => {
    const raw = '["tool", "get_todays_doses"]';
    expect(parseTurn(raw)).toEqual({ kind: 'unparseable', raw });
  });

  it('rejects an object with neither tool nor reply', () => {
    const raw = '{"message": "hello"}';
    expect(parseTurn(raw)).toEqual({ kind: 'unparseable', raw });
  });

  it('rejects a non-string reply', () => {
    const raw = '{"reply": 42}';
    expect(parseTurn(raw)).toEqual({ kind: 'unparseable', raw });
  });
});
