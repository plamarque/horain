import { describe, it, expect } from "@jest/globals";
import {
  routeAfterCallModel,
  routeAfterExecuteTools,
} from "../src/agent/graph.js";

describe("Routers", () => {
  it("routes to executeTools when tool calls are pending", async () => {
    const res = routeAfterCallModel({
      toolIteration: 1,
      pendingToolCalls: [{ id: "a", name: "list_projects", args: {} }],
    } as never);
    expect(res).toEqual("executeTools");
  });

  it("routes to finalize when no tool calls remain", async () => {
    const res = routeAfterCallModel({
      toolIteration: 1,
      pendingToolCalls: [],
    } as never);
    expect(res).toEqual("finalize");
  });

  it("loops back to callModel after successful tools execution", async () => {
    const res = routeAfterExecuteTools({
      toolIteration: 2,
      status: "running",
    } as never);
    expect(res).toEqual("callModel");
  });

  it("routes to finalize when maxToolIterations is reached from runtime config", async () => {
    const res = routeAfterCallModel(
      {
        toolIteration: 2,
        pendingToolCalls: [{ id: "a", name: "list_projects", args: {} }],
      } as never,
      {
        context: {
          maxToolIterations: 2,
        },
      } as never,
    );
    expect(res).toEqual("finalize");
  });
});
