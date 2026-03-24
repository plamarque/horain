import { env } from "node:process";

type JsonRpcSuccess<T> = {
  jsonrpc: "2.0";
  id: number;
  result: T;
};

type JsonRpcError = {
  jsonrpc: "2.0";
  id: number;
  error: { code: number; message: string; data?: unknown };
};

type JsonRpcResponse<T> = JsonRpcSuccess<T> | JsonRpcError;

export type RemoteToolSchema = {
  name: string;
  description?: string;
  inputSchema?: {
    type?: string;
    properties?: Record<string, unknown>;
    required?: string[];
  };
};

export class HorainMcpClient {
  private endpoint: string;
  private authToken: string;
  private requestId = 1;

  constructor() {
    this.endpoint =
      env.HORAIN_MCP_ENDPOINT ?? "http://localhost:8080/mcp";
    this.authToken = env.HORAIN_MCP_AUTH_TOKEN ?? env.HORAIN_API_KEY ?? "";
  }

  private async rpcCall<T>(
    method: string,
    params: Record<string, unknown>,
  ): Promise<T> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };
    if (this.authToken.length > 0) {
      headers.Authorization = `Bearer ${this.authToken}`;
    }

    const response = await fetch(this.endpoint, {
      method: "POST",
      headers,
      body: JSON.stringify({
        jsonrpc: "2.0",
        id: this.requestId++,
        method,
        params,
      }),
    });

    if (!response.ok) {
      throw new Error(
        `MCP HTTP error ${response.status}: ${response.statusText}`,
      );
    }

    const payload = (await response.json()) as JsonRpcResponse<T>;
    if ("error" in payload) {
      throw new Error(
        `MCP RPC error ${payload.error.code}: ${payload.error.message}`,
      );
    }

    return payload.result;
  }

  async listTools(): Promise<RemoteToolSchema[]> {
    const result = await this.rpcCall<{ tools: RemoteToolSchema[] }>(
      "tools/list",
      {},
    );
    return result.tools ?? [];
  }

  async callTool(
    name: string,
    args: Record<string, unknown>,
  ): Promise<unknown> {
    const result = await this.rpcCall<{ content?: unknown; data?: unknown }>(
      "tools/call",
      { name, arguments: args },
    );
    return result;
  }
}
