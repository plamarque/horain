import { DynamicStructuredTool } from "@langchain/core/tools";
import { z } from "zod";
import { HorainMcpClient } from "./mcpClient.js";
import { toolNames } from "./prompts.js";

const fallbackDescription =
  "Horain MCP tool. Use this when the user requests the matching capability.";

const toLangChainSchema = (
  _toolName: string,
  schema: unknown,
) => {
  if (
    typeof schema === "object" &&
    schema !== null &&
    "type" in schema &&
    (schema as { type?: string }).type === "object"
  ) {
    return z.object({}).passthrough();
  }
  return z.object({}).passthrough();
};

export const buildHorainTools = async (
  mcpClient: HorainMcpClient,
): Promise<DynamicStructuredTool[]> => {
  const availableTools = await mcpClient.listTools();
  const toolByName = new Map(
    availableTools.map((tool) => [tool.name, tool] as const),
  );

  return toolNames.map((toolName) => {
    const remote = toolByName.get(toolName);
    const description = remote?.description ?? fallbackDescription;
    const schema = toLangChainSchema(toolName, remote?.inputSchema);

    return new DynamicStructuredTool({
      name: toolName,
      description,
      schema,
      func: async (args) => {
        const result = await mcpClient.callTool(
          toolName,
          args as Record<string, unknown>,
        );
        return JSON.stringify(result);
      },
    });
  });
};
