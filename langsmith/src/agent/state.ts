import { BaseMessage, BaseMessageLike } from "@langchain/core/messages";
import { Annotation, messagesStateReducer } from "@langchain/langgraph";

export type ToolCallStep = {
  iteration: number;
  toolName: string;
  args: unknown;
  rawResult: unknown;
  llmContent: string;
};

export type AgentStatus = "running" | "success" | "tool_error" | "max_iterations";

export const StateAnnotation = Annotation.Root({
  messages: Annotation<BaseMessage[], BaseMessageLike[]>({
    reducer: messagesStateReducer,
    default: () => [],
  }),
  systemPrompt: Annotation<string>({
    reducer: (_left, right) => right,
    default: () => "",
  }),
  memoryBlock: Annotation<string>({
    reducer: (_left, right) => right,
    default: () => "",
  }),
  pendingToolCalls: Annotation<
    Array<{ id: string; name: string; args: Record<string, unknown> }>
  >({
    reducer: (_left, right) => right,
    default: () => [],
  }),
  toolSteps: Annotation<ToolCallStep[]>({
    reducer: (left, right) => [...left, ...right],
    default: () => [],
  }),
  toolIteration: Annotation<number>({
    reducer: (_left, right) => right,
    default: () => 0,
  }),
  status: Annotation<AgentStatus>({
    reducer: (_left, right) => right,
    default: () => "running",
  }),
  stopReason: Annotation<string>({
    reducer: (_left, right) => right,
    default: () => "",
  }),
  currentServerTimeBlock: Annotation<string>({
    reducer: (_left, right) => right,
    default: () => "",
  }),
});
