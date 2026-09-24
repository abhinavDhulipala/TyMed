// The orchestrator's own logic — confirmation handling, loop guards — against a scripted fake
// model. How the real Gemini Nano behaves is covered by src/ai/__evals__ (`npm run eval:ai`).
const mockGenerate = jest.fn<Promise<string>, [string]>();
const mockRunTool = jest.fn<Promise<unknown>, [string, unknown]>();

jest.mock('@/src/native/aiModule', () => ({
  generateNative: (prompt: string) => mockGenerate(prompt),
}));

jest.mock('@/src/db/medications', () => ({
  listMedications: async () => [{ name: 'Ibuprofen', dosage: '200 mg' }],
}));

jest.mock('../tools', () => ({
  TOOL_DESCRIPTIONS: {},
  runTool: (name: string, args: unknown) => mockRunTool(name, args),
}));

import { isAffirmative, isNegative, runTurn } from '../orchestrator';

const toolCall = (tool: string, args: Record<string, unknown>) => JSON.stringify({ tool, arguments: args });
const reply = (text: string) => JSON.stringify({ reply: text });

const ADD_ARGS = { name: 'Amoxicillin', dosage: '500 mg', times: ['08:00', '20:00'], durationDays: 7 };
const ADD_PENDING = {
  status: 'needs_confirmation',
  medicationName: 'Amoxicillin',
  dosage: '500 mg',
  times: ['08:00', '20:00'],
  endDate: '2026-09-30',
  similar: [],
};
const ADD_DONE = { status: 'added', medicationName: 'Amoxicillin', times: ['08:00', '20:00'], endDate: '2026-09-30' };

beforeEach(() => {
  mockGenerate.mockReset();
  mockRunTool.mockReset();
});

describe('confirmation flow', () => {
  it('asks a templated question for a needs_confirmation result and remembers the call as pending', async () => {
    mockGenerate.mockResolvedValueOnce(toolCall('add_medication', ADD_ARGS));
    mockRunTool.mockResolvedValueOnce(ADD_PENDING);

    const result = await runTurn('add amoxicillin 500 mg at 8am and 8pm for 7 days', []);

    // Phrased by the app from the result, not the model — no second model call.
    expect(mockGenerate).toHaveBeenCalledTimes(1);
    expect(result.reply).toMatch(/^Add Amoxicillin 500 mg at 8:00 AM and 8:00 PM every day through .*September 30\?$/);
    expect(result.pending).toEqual({ name: 'add_medication', arguments: ADD_ARGS });
  });

  it('applies the pending call with confirmed:true on a yes and reports it without the model', async () => {
    mockRunTool.mockResolvedValueOnce(ADD_DONE);

    const result = await runTurn('Yes', [], { name: 'add_medication', arguments: ADD_ARGS });

    expect(mockRunTool).toHaveBeenCalledTimes(1);
    expect(mockRunTool).toHaveBeenCalledWith('add_medication', { ...ADD_ARGS, confirmed: true });
    expect(mockGenerate).not.toHaveBeenCalled();
    expect(result.reply).toMatch(/^Done — added Amoxicillin at 8:00 AM and 8:00 PM/);
    expect(result.pending).toBeNull();
  });

  it('lets the model explain when the confirmed call fails', async () => {
    mockRunTool.mockResolvedValueOnce({ error: 'add_medication requires a non-empty name' });
    mockGenerate.mockResolvedValueOnce(reply('Sorry, that medication needs a name.'));

    const result = await runTurn('yes', [], { name: 'add_medication', arguments: ADD_ARGS });

    expect(result.reply).toBe('Sorry, that medication needs a name.');
  });

  it('describes a schedule change as before -> after', async () => {
    mockGenerate.mockResolvedValueOnce(toolCall('update_medication_schedule', { medicationName: 'Ibuprofen', times: ['08:00', '20:00'] }));
    mockRunTool.mockResolvedValueOnce({
      status: 'needs_confirmation',
      medicationName: 'Ibuprofen',
      before: { times: ['08:00'], endDate: null },
      after: { times: ['08:00', '20:00'], endDate: null },
    });

    const result = await runTurn('make ibuprofen twice a day', []);

    expect(result.reply).toBe('Change Ibuprofen from 8:00 AM every day to 8:00 AM and 8:00 PM every day?');
  });

  it('includes the tracked medications in the prompt', async () => {
    mockGenerate.mockResolvedValueOnce(reply('Hi!'));
    await runTurn('hello', []);
    expect(mockGenerate.mock.calls[0][0]).toContain('already tracks these medications: Ibuprofen 200 mg');
  });

  it('drops the pending call on a no, without calling the model or any tool', async () => {
    const result = await runTurn('no thanks', [], { name: 'add_medication', arguments: ADD_ARGS });

    expect(result.pending).toBeNull();
    expect(result.reply).toMatch(/won't/);
    expect(mockGenerate).not.toHaveBeenCalled();
    expect(mockRunTool).not.toHaveBeenCalled();
  });

  it('hands a qualified answer back to the model and forgets the stale pending call', async () => {
    mockGenerate.mockResolvedValueOnce(reply('What time instead?'));

    const result = await runTurn('yes but make it 9pm', [], { name: 'add_medication', arguments: ADD_ARGS });

    expect(mockRunTool).not.toHaveBeenCalled();
    expect(result.pending).toBeNull();
  });

  it('strips confirmed:true from model tool calls so the model can never confirm on its own', async () => {
    mockGenerate.mockResolvedValueOnce(toolCall('mark_dose_taken', { medicationName: 'Aspirin', confirmed: true }));
    mockRunTool.mockResolvedValueOnce({ status: 'needs_confirmation', medicationName: 'Aspirin', scheduledTime: '08:00' });

    const result = await runTurn('I took my aspirin', []);

    expect(mockRunTool).toHaveBeenCalledWith('mark_dose_taken', { medicationName: 'Aspirin' });
    expect(result.reply).toBe('Mark your 8:00 AM Aspirin dose as taken?');
  });
});

describe('unbacked write claims', () => {
  it('pushes back once when the model claims a write without a saved tool result', async () => {
    mockGenerate
      .mockResolvedValueOnce(reply("Okay, I've marked your aspirin dose as taken."))
      .mockResolvedValueOnce(toolCall('mark_dose_taken', { medicationName: 'aspirin' }));
    mockRunTool.mockResolvedValueOnce({ status: 'needs_confirmation', medicationName: 'Aspirin', scheduledTime: '08:00' });

    const result = await runTurn('I just took my aspirin', []);

    expect(mockGenerate.mock.calls[1][0]).toContain('You have NOT changed anything');
    expect(result.reply).toBe('Mark your 8:00 AM Aspirin dose as taken?');
  });

  it('falls back rather than show a second unbacked claim', async () => {
    mockGenerate.mockResolvedValue(reply("I've added it for you."));

    const result = await runTurn('add aspirin', []);

    expect(result.reply).toMatch(/didn't catch that/);
  });

  it('lets ordinary replies through', async () => {
    mockGenerate.mockResolvedValueOnce(reply('You have aspirin at 8:00 AM left today.'));
    const result = await runTurn("what's left", []);
    expect(result.reply).toBe('You have aspirin at 8:00 AM left today.');
  });
});

describe('loop guards', () => {
  it('stops with a fallback when the model repeats the same tool call', async () => {
    mockGenerate.mockResolvedValue(toolCall('get_todays_doses', {}));
    mockRunTool.mockResolvedValue([]);

    const result = await runTurn("what's left today", []);

    expect(result.reply).toMatch(/didn't catch that/);
    expect(mockRunTool).toHaveBeenCalledTimes(1);
  });

  it('retries once with a correction after unparseable output, then falls back', async () => {
    mockGenerate.mockResolvedValue('not json at all');

    const result = await runTurn('hello', []);

    expect(mockGenerate).toHaveBeenCalledTimes(2);
    expect(result.reply).toMatch(/didn't catch that/);
  });
});

describe('isAffirmative / isNegative', () => {
  it.each(['Yes', 'yes.', 'Yep', 'ok', 'Sounds good!', "that's right", 'go ahead'])('treats "%s" as yes', (text) => {
    expect(isAffirmative(text)).toBe(true);
  });

  it.each(['yes but 9pm', 'no', 'Yesterday I took it', 'what?', 'ok, change it to 9pm'])('does not treat "%s" as yes', (text) => {
    expect(isAffirmative(text)).toBe(false);
  });

  it.each(['No', 'nope', 'cancel', 'never mind', "don't"])('treats "%s" as no', (text) => {
    expect(isNegative(text)).toBe(true);
  });
});
