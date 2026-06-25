import test from "node:test";
import assert from "node:assert/strict";
import {
  averageThroughput,
  computeBudget,
  buildRequestMessages,
  computeRunMetrics,
  estimateTokens,
  modelLimitsFromModel,
  modelSummaryParts,
  normalizeModelList,
  preparePrompt,
  qualityNotes,
  stripThinking,
} from "../src/metrics.js";

test("stripThinking removes model reasoning tags and keeps visible answer", () => {
  assert.equal(stripThinking("<think>hidden reasoning</think>\n\nFinal answer."), "Final answer.");
});

test("computeRunMetrics uses API completion token count when available", () => {
  const metrics = computeRunMetrics({
    startedAt: 1000,
    endedAt: 3500,
    text: "hello world",
    usage: { completion_tokens: 25 },
  });

  assert.equal(metrics.elapsedMs, 2500);
  assert.equal(metrics.completionTokens, 25);
  assert.equal(metrics.tokensPerSecond, 10);
});

test("computeRunMetrics protects Chinese output from low API token estimates", () => {
  const metrics = computeRunMetrics({
    startedAt: 0,
    endedAt: 1000,
    text: "本地手机模型适合快速原型开发。",
    usage: { completion_tokens: 1 },
  });

  assert.ok(metrics.completionTokens > 1);
});

test("estimateTokens handles English and Chinese text", () => {
  assert.ok(estimateTokens("hello local phone model") >= 4);
  assert.ok(estimateTokens("你好，本地手机模型。") >= 4);
});

test("qualityNotes calls out leaked think blocks", () => {
  const result = qualityNotes("<think>scratchpad</think>\n\n答案完成。");

  assert.equal(result.visible, "答案完成。");
  assert.ok(result.notes.some((note) => note.includes("<think>")));
});

test("averageThroughput averages finite samples", () => {
  assert.equal(
    averageThroughput([
      { tokensPerSecond: 4 },
      { tokensPerSecond: 8 },
    ]),
    6,
  );
});

test("buildRequestMessages keeps history without duplicating the current prompt", () => {
  const messages = buildRequestMessages({
    systemPrompt: "Be concise.",
    prompt: "Current question",
    history: [
      { role: "user", text: "Previous question" },
      { role: "assistant", visibleText: "Previous answer", text: "<think>x</think>Previous answer" },
      { role: "error", text: "Ignore this" },
    ],
  });

  assert.deepEqual(messages, [
    { role: "system", content: "Be concise." },
    { role: "user", content: "Previous question" },
    { role: "assistant", content: "Previous answer" },
    { role: "user", content: "Current question" },
  ]);
});

test("buildRequestMessages can isolate benchmark prompts from chat history", () => {
  const messages = buildRequestMessages({
    systemPrompt: "Be concise.",
    prompt: "Benchmark question",
    history: [{ role: "user", text: "Unrelated previous topic" }],
    includeHistory: false,
  });

  assert.deepEqual(messages, [
    { role: "system", content: "Be concise." },
    { role: "user", content: "Benchmark question" },
  ]);
});

test("preparePrompt adds no-think directive only in speed mode", () => {
  assert.equal(preparePrompt("你好", true), "/no_think 你好");
  assert.equal(preparePrompt("/no_think 你好", true), "/no_think 你好");
  assert.equal(preparePrompt("你好", false), "你好");
});

test("buildRequestMessages applies speed mode to the sent prompt", () => {
  const messages = buildRequestMessages({
    systemPrompt: "Be concise.",
    prompt: "快速回答",
    includeHistory: false,
    speedMode: true,
  });

  assert.equal(messages.at(-1).content, "/no_think 快速回答");
});

test("normalizeModelList prefers active installed models", () => {
  const result = normalizeModelList({
    recommended_model: "qwen3-0.6b-mixed-int4",
    data: [
      { id: "qwen3-0.6b-mixed-int4", installed: true, active: false },
      { id: "qwen3-0.6b-dynamic-int8", installed: true, active: true },
      { id: "missing", installed: false, active: false },
    ],
  });

  assert.equal(result.selectedModelId, "qwen3-0.6b-dynamic-int8");
  assert.equal(result.selectableModels.length, 2);
});

test("normalizeModelList keeps incompatible installed models selectable but does not auto-select them", () => {
  const result = normalizeModelList({
    recommended_model: "gemma4-e4b-it",
    data: [
      { id: "qwen3-0.6b-mixed-int4", installed: true, compatible: true },
      { id: "gemma4-e4b-it", installed: true, compatible: false },
    ],
  });

  assert.equal(result.selectedModelId, "qwen3-0.6b-mixed-int4");
  assert.equal(result.selectableModels.length, 2);
});

test("modelSummaryParts includes compatibility hardware and tier", () => {
  assert.deepEqual(
    modelSummaryParts({
      installed: true,
      compatible: false,
      context_tokens: 2048,
      performance_tier: "quality",
      hardware_target: "generic_cpu",
      min_ram_mb: 10240,
    }),
    ["已安装", "当前设备不推荐", "quality", "generic_cpu", "2048 ctx", ">= 10240 MB RAM"],
  );
});

test("modelLimitsFromModel reads selected model context limits", () => {
  assert.deepEqual(
    modelLimitsFromModel({
      context_tokens: 4096,
      recommended_output_tokens: 256,
      max_output_tokens: 4096,
    }),
    {
      contextTokens: 4096,
      recommendedOutputTokens: 256,
      maxOutputTokens: 4096,
    },
  );
});

test("computeBudget counts prompt history and reserved output against context", () => {
  const budget = computeBudget({
    contextTokens: 2048,
    outputTokens: 256,
    systemPrompt: "你是简洁助手。",
    prompt: "请解释本地模型。",
    history: [
      { role: "user", text: "之前的问题" },
      { role: "assistant", visibleText: "之前的回答" },
    ],
  });

  assert.equal(budget.contextTokens, 2048);
  assert.equal(budget.outputTokens, 256);
  assert.ok(budget.inputTokens > 0);
  assert.equal(budget.totalTokens, budget.inputTokens + 256);
  assert.ok(budget.remainingTokens > 0);
  assert.equal(budget.isOverBudget, false);
});

test("computeBudget flags context overflow", () => {
  const budget = computeBudget({
    contextTokens: 32,
    outputTokens: 24,
    systemPrompt: "system",
    prompt: "这是一个很长很长很长很长很长的中文输入。",
    history: [],
  });

  assert.equal(budget.isOverBudget, true);
  assert.ok(budget.remainingTokens < 0);
});
