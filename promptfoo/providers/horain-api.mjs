/**
 * Custom Promptfoo provider for Horain chat API.
 * POSTs to /chat/message and returns the full response as JSON string
 * so assertions can check message, data.chart, data.timeLogs, toolCalls.
 */
const API_BASE = process.env.PROMPTFOO_API_URL || 'http://localhost:8080';
const API_KEY = process.env.HORAIN_API_KEY || 'HORAIN_DEV_KEY';

export default class HorainApiProvider {
  id = () => 'horain-api';

  async callApi(prompt, context, options) {
    const url = `${API_BASE.replace(/\/$/, '')}/chat/message`;
    let history = [];
    if (context?.vars?.history && Array.isArray(context.vars.history)) {
      history = context.vars.history.map((h) =>
        typeof h === 'object' && h !== null
          ? { role: h.role || 'user', content: String(h.content ?? '') }
          : { role: 'user', content: String(h) }
      );
    } else if (context?.vars?.messages && Array.isArray(context.vars.messages)) {
      history = context.vars.messages.map((m) =>
        typeof m === 'object' && m !== null
          ? { role: m.role || (m.user !== undefined ? 'user' : 'assistant'), content: String(m.content ?? m.user ?? m.assistant ?? '') }
          : { role: 'user', content: String(m) }
      );
    }
    let contextEntries = [];
    if (context?.vars?.contextEntries && Array.isArray(context.vars.contextEntries)) {
      contextEntries = context.vars.contextEntries
        .filter((e) => typeof e === 'object' && e != null && e.id != null)
        .map((e) => ({
          id: e.id,
          projectId: e.projectId,
          projectName: e.projectName,
          durationMinutes: e.durationMinutes,
          note: e.note,
          loggedAt: e.loggedAt,
        }));
    }
    const body = { message: prompt, history };
    if (contextEntries.length > 0) body.contextEntries = contextEntries;
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${API_KEY}`,
        },
        body: JSON.stringify(body),
      });

      const text = await res.text();
      let json;
      try {
        json = text ? JSON.parse(text) : {};
      } catch {
        return { error: `Invalid JSON response: ${text.slice(0, 200)}` };
      }

      if (!res.ok) {
        return {
          error: json.error || json.message || `HTTP ${res.status}: ${text.slice(0, 200)}`,
        };
      }

      const output = JSON.stringify({
        message: json.assistantMessage || '',
        data: json.data || null,
        toolCalls: (json.toolCalls || []).map((t) => ({
          name: t.name,
          arguments: t.arguments,
          result: t.result ? t.result.slice(0, 200) : undefined,
        })),
      });

      return { output };
    } catch (e) {
      return { error: e.message || String(e) };
    }
  }
}
