# Agent Design and Context Engineering

## Purpose

This document is the normative source for **agent architecture** and **tool design** in Horain. It captures principles of *context engineering*: the primary constraint for LLM systems is **context management**, not APIs. All design choices for tools, prompts, and agent structure must support reliable behaviour within a limited context window.

---

## Core Principle

The main constraint when designing LLM systems is **context management**, not APIs.

LLM agents operate inside a limited context window. Everything they know or can do must be represented inside that context. Therefore:

- **Tool definitions**, **tool outputs**, and **prompts** must be designed to **steer the model** toward correct behaviour.

This practice is called **context engineering**.

---

## 1. Tools Are Prompt Extensions

Tools are not just APIs. A tool definition acts as an **instruction layer for the model**.

Every tool must contain:

- A clear **purpose** (what the tool does)
- Explicit guidance on **when it should be used**
- Explicit guidance on **when it should NOT be used**
- Examples of valid calls (when space allows)
- Explanations of parameters
- Hints that help the model avoid misuse

Tool descriptions must be written **for the LLM**, not for human developers. They are part of the **system prompt architecture**.

**Example structure for each tool (in spec and in code):**

- Purpose  
- When to use  
- When not to use  
- Example calls  
- Parameter explanation  
- Expected output format  

**Good:** "Search for projects by name. Use when the user mentions a project to log time on. When no match is found, may return close_matches; propose the first one and ask for confirmation before logging. Do NOT use to list all projects—use list_projects instead."

**Bad:** "Searches projects." (no when-to-use / when-not-to-use, no guidance for the LLM.)

---

## 2. Tool Outputs Must Be LLM-Oriented

Raw API responses are not optimal for LLM reasoning. Tools should return outputs optimized for:

- **Readability**
- **Reasoning**
- **Structured understanding**

**Prefer:**

- Markdown sections
- Labeled fields
- Short explanations
- Structured summaries

**Avoid:**

- Raw database-style responses
- Verbose, unstructured JSON blobs
- Unstructured logs

The goal is to **improve reasoning reliability** of the model. Think of tool outputs as **context artifacts for the LLM**.

**Implementation (Horain):** Tools return a dual payload: **llm** (string sent to the model) and **data** (structured JSON for the app). Only the **llm** part is injected into the conversation; the client receives the full result for trace and structured display (cards, charts). See [MCP_TOOLS.md](MCP_TOOLS.md).

---

## 3. Tools Must Minimize Model Errors

LLMs frequently misuse tools when interfaces are complex. Therefore tools should follow:

- **Minimal number of parameters**
- **Descriptive parameter names**
- **Default values** whenever possible
- **Examples of correct invocation**
- **Explicit warnings** for invalid use

Tools should be designed for **robust tool calling**, not for API completeness.

**Good:** A tool with 2–4 well-named parameters, defaults where sensible, and a description that says "Do NOT call without a project id from search_project or list_projects."

**Bad:** A generic tool with many optional parameters and no guidance on valid combinations.

---

## 4. Context Is a Scarce Resource

LLM context windows degrade as they grow (sometimes called "context rot"). Systems must minimize unnecessary context growth.

**Best practices:**

- Keep tool descriptions **concise but informative**
- Avoid **large static prompts** where possible
- **Summarize historical data** when feeding it back into context
- **Isolate tasks** into smaller contexts when feasible

The architecture should assume **context degradation over time**.

---

## 5. Prefer Specialized Tools Over Generic APIs

Instead of exposing many generic endpoints, design **task-oriented tools**.

**Bad example:**  
Tool: `query_database` (generic; the model must plan the query and interpret raw results.)

**Good example:**  
Tool: `get_project_hours_summary` or `sum_time_by_project` (clear capability; the model knows exactly when to call it and what it returns.)

Tools should represent **clear capabilities**. This reduces planning errors by the LLM.

---

## 6. Use Sub-Agents When Complexity Grows

When tasks become complex or tools become numerous, prefer **sub-agents**.

A sub-agent has:

- Its own **system prompt**
- A **restricted tool set**
- A **focused objective**

**Benefits:**

- Reduces context pollution
- Improves reasoning reliability
- Isolates tool usage

This architecture should be considered when agent capabilities expand (e.g. many new tools or distinct workflows). See [ARCH.md](ARCH.md) for how this may apply to Horain’s evolution.

**Re-evaluation criterion:** When adding a large number of new tools or clearly distinct workflows (e.g. “advanced reports” vs “quick log”), re-evaluate whether a dedicated sub-agent (own prompt + restricted tool set + focused objective) would reduce context size and improve reliability. No fixed threshold; use judgement based on prompt length, tool count, and observed model behaviour.

---

## 7. Documentation Must Support AI Coding Agents

All architectural documentation should be structured so that **AI coding agents** (Cursor, Claude Code, Codex, etc.) can easily understand:

- **What tools exist** (see [MCP_TOOLS.md](MCP_TOOLS.md))
- **When to generate new tools** (task-oriented; when a clear new capability is needed)
- **How tools should be designed** (this document + MCP_TOOLS template)
- **How outputs should be structured** (LLM-oriented; see section 2 above)

Documentation should explicitly guide the AI toward these patterns so that new code and new tools follow the same conventions.

---

## Reference

- *Context Management and MCP*, David Cramer — https://cra.mr/context-management-and-mcp/
