# Horain LangGraph Exploration

This folder hosts an exploratory LangGraph replica of Horain's agentic core:

- orchestration loop (`callModel -> executeTools -> callModel`);
- Horain-style system prompt and memory injection;
- remote MCP tool calls (MCP server stays in `backend/`);
- LangSmith Studio-first tracing.

This is a lab environment. It is not a production migration.

## Architecture

`src/agent/graph.ts` contains a multi-node graph:

1. `prepareContext`: builds memory block + server time block
2. `buildPrompt`: assembles full Horain-style system prompt
3. `callModel`: runs LLM with tool-calling enabled
4. `executeTools`: executes pending tools on remote MCP backend
5. `finalize`: stops with success/tool_error/max_iterations

`src/agent/mcpClient.ts` is the only place where remote MCP transport is handled.

## Local setup

1. Copy environment:

```bash
cp .env.example .env
```

2. Fill required vars:

- `OPENAI_API_KEY`
- `LANGSMITH_API_KEY`
- `HORAIN_MCP_ENDPOINT` (default `http://localhost:8080/mcp`)
- `HORAIN_MCP_AUTH_TOKEN` (optional)

When running with `./scripts/start-langgraph.sh`, backend `.env` is loaded first.
If `HORAIN_MCP_AUTH_TOKEN` is empty, the agent client falls back to `HORAIN_API_KEY`.

3. Install dependencies:

```bash
npm install
```

4. Start local graph server:

```bash
npx @langchain/langgraph-cli dev
```

5. Open Studio from CLI output and connect to local server.

## Exploration scenarios

Use these six scenarios to inspect traces and compare behavior with backend Horain:

1. Simple log: "J'ai travaillé 2h sur Horain."
2. Multi-entry: "2h sur Horain et 30 min sur Festibask."
3. Project disambiguation: typo or ambiguous project name.
4. Entries listing for this week.
5. Analytics chart request (hours by project this week).
6. Memory use and recall after explicit user confirmation.

For each scenario, inspect:

- node transitions;
- tool selection and arguments;
- dual tool payload behavior (`llm` vs raw result);
- stop reason (`final_answer`, `tool_error`, `max_iterations`).

## Notes

- This replica intentionally prioritizes observability and iteration speed.
- MCP server logic remains authoritative in `backend/`.
- Prompt behavior is inspired by Horain backend (`LlmChatService`) and designed for Studio experimentation.
- MCP quick check:

```bash
node -r dotenv/config -e 'const endpoint=process.env.HORAIN_MCP_ENDPOINT||"http://localhost:8080/mcp"; fetch(endpoint,{method:"POST",headers:{"Content-Type":"application/json","Authorization":"Bearer "+(process.env.HORAIN_MCP_AUTH_TOKEN||process.env.HORAIN_API_KEY||"")},body:JSON.stringify({jsonrpc:"2.0",id:1,method:"tools/list",params:{}})}).then(async r=>{console.log("status",r.status);console.log(await r.text())})'
```
