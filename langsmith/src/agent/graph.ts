import { AIMessage, SystemMessage, ToolMessage } from "@langchain/core/messages";
import { RunnableConfig } from "@langchain/core/runnables";
import { DynamicStructuredTool } from "@langchain/core/tools";
import { StateGraph } from "@langchain/langgraph";
import { withLangGraph } from "@langchain/langgraph/zod";
import { ChatOpenAI } from "@langchain/openai";
import { env } from "node:process";
import { z } from "zod/v3";
import { HorainMcpClient } from "./mcpClient.js";
import { MAX_TOOL_ITERATIONS, HORAIN_SYSTEM_PROMPT } from "./prompts.js";
import { StateAnnotation } from "./state.js";
import { buildHorainTools } from "./tools.js";

const mcpClient = new HorainMcpClient();
let cachedToolsPromise: Promise<DynamicStructuredTool[]> | null = null;
const defaultModelName = env.LLM_MODEL ?? "gpt-4o-mini";

const RuntimeConfigSchema = z.object({
  systemPrompt: withLangGraph(z.string().default(HORAIN_SYSTEM_PROMPT), {
    jsonSchemaExtra: {
      langgraph_nodes: ["buildPrompt"],
      langgraph_type: "prompt",
      description: "Base system prompt for Horain assistant behavior.",
    },
  }),
  model: withLangGraph(z.string().default(defaultModelName), {
    jsonSchemaExtra: {
      langgraph_nodes: ["callModel"],
      description: "LLM identifier in provider/model format.",
    },
  }),
  temperature: withLangGraph(z.number().min(0).max(2).default(0), {
    jsonSchemaExtra: {
      langgraph_nodes: ["callModel"],
      description: "Sampling temperature for LLM generation.",
    },
  }),
  maxToolIterations: withLangGraph(
    z.number().int().min(1).default(MAX_TOOL_ITERATIONS),
    {
      jsonSchemaExtra: {
        langgraph_nodes: ["callModel", "executeTools", "finalize"],
        description: "Maximum tool loop iterations before forced finalization.",
      },
    },
  ),
  includeMemoryBlock: withLangGraph(z.boolean().default(true), {
    jsonSchemaExtra: {
      langgraph_nodes: ["prepareContext", "buildPrompt"],
      description: "Whether to include persisted memories in system prompt.",
    },
  }),
  includeCurrentServerTimeBlock: withLangGraph(z.boolean().default(true), {
    jsonSchemaExtra: {
      langgraph_nodes: ["prepareContext", "buildPrompt"],
      description: "Whether to include current server time block in prompt.",
    },
  }),
});

type RuntimeConfig = z.infer<typeof RuntimeConfigSchema>;

const parseRuntimeConfig = (config?: RunnableConfig): RuntimeConfig => {
  const rawContext = ((config as { context?: unknown } | undefined)?.context ??
    (config as { configurable?: unknown } | undefined)?.configurable ??
    {}) as unknown;
  const parsed = RuntimeConfigSchema.safeParse(rawContext);
  if (parsed.success) {
    return parsed.data;
  }
  return RuntimeConfigSchema.parse({});
};

const getTools = async (): Promise<DynamicStructuredTool[]> => {
  if (cachedToolsPromise === null) {
    cachedToolsPromise = buildHorainTools(mcpClient);
  }
  return cachedToolsPromise;
};

const getModel = (runtimeConfig: RuntimeConfig): ChatOpenAI =>
  new ChatOpenAI({
    apiKey: env.LLM_API_KEY ?? env.OPENAI_API_KEY,
    model: runtimeConfig.model,
    temperature: runtimeConfig.temperature,
  });

const stringifyContent = (value: unknown): string => {
  if (typeof value === "string") {
    return value;
  }
  return JSON.stringify(value);
};

const extractToolCalls = (
  response: AIMessage,
): Array<{ id: string; name: string; args: Record<string, unknown> }> => {
  const direct =
    "tool_calls" in response
      ? (response.tool_calls as Array<{
          id?: string;
          name?: string;
          args?: Record<string, unknown>;
        }>)
      : [];

  return (direct ?? [])
    .filter((tc) => typeof tc.name === "string")
    .map((tc, index) => ({
      id: tc.id ?? `tool_${index}`,
      name: tc.name as string,
      args: (tc.args ?? {}) as Record<string, unknown>,
    }));
};

const buildCurrentServerTimeBlock = (): string => {
  const now = new Date();
  const startOfToday = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 0, 0, 0),
  );
  const endOfToday = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 23, 59, 59),
  );
  const startOfWeek = new Date(startOfToday);
  startOfWeek.setUTCDate(startOfWeek.getUTCDate() - startOfWeek.getUTCDay());
  const endOfWeek = new Date(startOfWeek);
  endOfWeek.setUTCDate(endOfWeek.getUTCDate() + 6);
  endOfWeek.setUTCHours(23, 59, 59, 0);
  const startOfMonth = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1, 0, 0, 0),
  );
  const endOfMonth = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 0, 23, 59, 59),
  );

  return [
    "## Current server time",
    `- iso: ${now.toISOString()}`,
    "- timezone: UTC",
    `- startOfToday: ${startOfToday.toISOString()}`,
    `- endOfToday: ${endOfToday.toISOString()}`,
    `- startOfWeek: ${startOfWeek.toISOString()}`,
    `- endOfWeek: ${endOfWeek.toISOString()}`,
    `- startOfMonth: ${startOfMonth.toISOString()}`,
    `- endOfMonth: ${endOfMonth.toISOString()}`,
  ].join("\n");
};

const buildMemoryBlock = async (): Promise<string> => {
  try {
    const result = await mcpClient.callTool("get_memories", {});
    const root =
      typeof result === "object" && result !== null ? result : {};
    const data =
      "data" in root &&
      typeof root.data === "object" &&
      root.data !== null
        ? (root.data as { memories?: Array<{ factText?: string }> })
        : (root as { memories?: Array<{ factText?: string }> });
    const memories = data.memories ?? [];
    if (memories.length === 0) {
      return "[Memories]\nNo stored memories.";
    }
    const lines = memories
      .slice(0, 30)
      .map((m) => m.factText)
      .filter((text): text is string => typeof text === "string" && text.length > 0)
      .map((text) => `- ${text}`);
    return `[Memories]\n${lines.join("\n")}`;
  } catch {
    return "[Memories]\nNo stored memories.";
  }
};

const prepareContext = async (
  _state: typeof StateAnnotation.State,
  config: RunnableConfig,
): Promise<Record<string, unknown>> => {
  const runtimeConfig = parseRuntimeConfig(config);
  const memoryBlock = runtimeConfig.includeMemoryBlock
    ? await buildMemoryBlock()
    : "[Memories]\nDisabled by runtime configuration.";
  const currentServerTimeBlock = runtimeConfig.includeCurrentServerTimeBlock
    ? buildCurrentServerTimeBlock()
    : "";
  return {
    memoryBlock,
    currentServerTimeBlock,
    status: "running",
    stopReason: "",
  };
};

const buildPrompt = async (
  state: typeof StateAnnotation.State,
  config: RunnableConfig,
): Promise<Record<string, unknown>> => {
  const runtimeConfig = parseRuntimeConfig(config);
  const promptParts = [
    runtimeConfig.systemPrompt,
    state.memoryBlock,
    state.currentServerTimeBlock,
  ].filter((part) => part.length > 0);
  const systemPrompt = promptParts.join("\n\n");
  return { systemPrompt };
};

const callModel = async (
  state: typeof StateAnnotation.State,
  config: RunnableConfig,
): Promise<Record<string, unknown>> => {
  const runtimeConfig = parseRuntimeConfig(config);
  const model = getModel(runtimeConfig);
  const tools = await getTools();
  const boundModel = model.bindTools(tools);
  const input = [new SystemMessage(state.systemPrompt), ...state.messages];
  const response = (await boundModel.invoke(input)) as AIMessage;
  const pendingToolCalls = extractToolCalls(response);

  return {
    messages: [response],
    pendingToolCalls,
    toolIteration: state.toolIteration + 1,
    status: pendingToolCalls.length > 0 ? "running" : "success",
    stopReason: pendingToolCalls.length > 0 ? "" : "final_answer",
  };
};

const executeTools = async (
  state: typeof StateAnnotation.State,
): Promise<Record<string, unknown>> => {
  const toolMessages: ToolMessage[] = [];
  const toolSteps = [];
  let status = state.status;
  let stopReason = state.stopReason;

  const previousDeleteCalls = state.toolSteps.filter(
    (step) => step.toolName === "delete_time_log",
  ).length;

  for (const call of state.pendingToolCalls) {
    try {
      if (call.name === "delete_time_log" && previousDeleteCalls > 3) {
        const guardText =
          "Mass deletion guard: explicit user confirmation required before deleting more than 3 entries.";
        toolMessages.push(
          new ToolMessage({
            tool_call_id: call.id,
            content: guardText,
          }),
        );
        toolSteps.push({
          iteration: state.toolIteration,
          toolName: call.name,
          args: call.args,
          rawResult: { error: guardText },
          llmContent: guardText,
        });
        status = "tool_error";
        stopReason = "mass_delete_guard";
        continue;
      }

      const result = await mcpClient.callTool(call.name, call.args);
      const llmContent =
        typeof result === "object" &&
        result !== null &&
        "llm" in result &&
        typeof result.llm === "string"
          ? result.llm
          : stringifyContent(result);
      toolMessages.push(
        new ToolMessage({
          tool_call_id: call.id,
          content: llmContent,
        }),
      );
      toolSteps.push({
        iteration: state.toolIteration,
        toolName: call.name,
        args: call.args,
        rawResult: result,
        llmContent,
      });
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Unknown MCP error";
      const errorText = `Tool ${call.name} failed: ${message}`;
      toolMessages.push(
        new ToolMessage({
          tool_call_id: call.id,
          content: errorText,
        }),
      );
      toolSteps.push({
        iteration: state.toolIteration,
        toolName: call.name,
        args: call.args,
        rawResult: { error: message },
        llmContent: errorText,
      });
      status = "tool_error";
      stopReason = "tool_error";
    }
  }

  return {
    messages: toolMessages,
    toolSteps,
    pendingToolCalls: [],
    status,
    stopReason,
  };
};

const finalize = async (
  state: typeof StateAnnotation.State,
  config: RunnableConfig,
): Promise<Record<string, unknown>> => {
  const runtimeConfig = parseRuntimeConfig(config);
  if (state.status === "running" && state.toolIteration >= runtimeConfig.maxToolIterations) {
    return {
      status: "max_iterations",
      stopReason: "max_iterations",
      messages: [
        new AIMessage(
          "I'm sorry, I reached the maximum number of tool iterations. Please try a simpler request.",
        ),
      ],
    };
  }
  return {};
};

export const routeAfterCallModel = (
  state: typeof StateAnnotation.State,
  config?: RunnableConfig,
): "executeTools" | "finalize" => {
  const runtimeConfig = parseRuntimeConfig(config);
  if (state.toolIteration >= runtimeConfig.maxToolIterations) {
    return "finalize";
  }
  return state.pendingToolCalls.length > 0 ? "executeTools" : "finalize";
};

export const routeAfterExecuteTools = (
  state: typeof StateAnnotation.State,
  config?: RunnableConfig,
): "callModel" | "finalize" => {
  const runtimeConfig = parseRuntimeConfig(config);
  if (state.toolIteration >= runtimeConfig.maxToolIterations) {
    return "finalize";
  }
  if (state.status === "tool_error") {
    return "finalize";
  }
  return "callModel";
};

const builder = new StateGraph(StateAnnotation, { context: RuntimeConfigSchema })
  .addNode("prepareContext", prepareContext)
  .addNode("buildPrompt", buildPrompt)
  .addNode("callModel", callModel)
  .addNode("executeTools", executeTools)
  .addNode("finalize", finalize)
  .addEdge("__start__", "prepareContext")
  .addEdge("prepareContext", "buildPrompt")
  .addEdge("buildPrompt", "callModel")
  .addConditionalEdges("callModel", routeAfterCallModel)
  .addConditionalEdges("executeTools", routeAfterExecuteTools)
  .addEdge("finalize", "__end__");

export const graph = builder.compile();
graph.name = "Horain LangGraph Replica";
