export function estimateTokens(text) {
  const trimmed = text.trim();
  if (!trimmed) return 0;
  const cjk = (trimmed.match(/[\u3400-\u9fff]/g) || []).length;
  const words = (trimmed.match(/[A-Za-z0-9_]+(?:[-'][A-Za-z0-9_]+)*/g) || []).length;
  const punctuation = (trimmed.match(/[^\sA-Za-z0-9_\u3400-\u9fff]/g) || []).length;
  return Math.max(1, Math.round(cjk * 0.75 + words * 1.25 + punctuation * 0.25));
}

export function stripThinking(text) {
  const withoutThink = text.replace(/<think>[\s\S]*?<\/think>/gi, "").trim();
  return withoutThink || text.trim();
}

export function computeRunMetrics({ startedAt, endedAt, text, usage }) {
  const elapsedMs = Math.max(1, endedAt - startedAt);
  const completionTokens = Math.max(usage?.completion_tokens || 0, estimateTokens(text));
  const tokensPerSecond = completionTokens / (elapsedMs / 1000);
  return {
    elapsedMs,
    completionTokens,
    tokensPerSecond,
  };
}

export function qualityNotes(rawText) {
  const visible = stripThinking(rawText);
  const notes = [];
  if (/<think>/i.test(rawText)) {
    notes.push("输出包含 <think> 推理段，评估最终答案时建议看剥离后的内容。");
  }
  if (visible.length < 12) {
    notes.push("最终答案偏短，适合测延迟，不太适合判断质量。");
  }
  if (visible.length > 1200) {
    notes.push("答案较长，适合观察稳定性和吞吐速度。");
  }
  if (!/[。.!?？]$/.test(visible.trim())) {
    notes.push("最终答案没有明显句末标点，可能被 max_tokens 截断。");
  }
  return {
    visible,
    notes: notes.length ? notes : ["最终答案可直接阅读。"],
  };
}

export function averageThroughput(samples) {
  const values = samples.map((sample) => sample.tokensPerSecond).filter(Number.isFinite);
  if (!values.length) return 0;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

export const defaultModelLimits = {
  contextTokens: 2048,
  recommendedOutputTokens: 256,
  maxOutputTokens: 2048,
};

export function modelLimitsFromModel(model) {
  return {
    contextTokens: Number(model?.context_tokens) || defaultModelLimits.contextTokens,
    recommendedOutputTokens:
      Number(model?.recommended_output_tokens) || defaultModelLimits.recommendedOutputTokens,
    maxOutputTokens: Number(model?.max_output_tokens) || defaultModelLimits.maxOutputTokens,
  };
}

export function normalizeModelList(payload) {
  const models = Array.isArray(payload?.data) ? payload.data : [];
  const selectableModels = models.filter((model) => model.installed);
  const compatibleInstalled = selectableModels.filter((model) => model.compatible !== false);
  const active = compatibleInstalled.find((model) => model.active);
  const recommended = compatibleInstalled.find((model) => model.id === payload?.recommended_model);
  const selectedModel = active || recommended || compatibleInstalled[0] || selectableModels[0] || models[0] || null;

  return {
    models,
    selectableModels,
    selectedModelId: selectedModel?.id || "",
  };
}

export function modelSummaryParts(model, fallbackLimits = defaultModelLimits) {
  if (!model) return ["没有可用模型"];
  return [
    model.installed ? "已安装" : "未安装",
    model.compatible === false ? "当前设备不推荐" : "当前设备可用",
    model.performance_tier,
    model.hardware_target,
    `${model.context_tokens || fallbackLimits.contextTokens} ctx`,
    model.min_ram_mb ? `>= ${model.min_ram_mb} MB RAM` : "",
  ].filter(Boolean);
}

export function buildRequestMessages({
  systemPrompt,
  prompt,
  history = [],
  includeHistory = true,
  historyLimit = 8,
  speedMode = false,
}) {
  const messages = [{ role: "system", content: systemPrompt }];
  if (includeHistory) {
    messages.push(
      ...history
        .filter((message) => message.role === "user" || message.role === "assistant")
        .slice(-historyLimit)
        .map((message) => ({
          role: message.role,
          content: message.visibleText || message.text,
        })),
    );
  }
  messages.push({ role: "user", content: preparePrompt(prompt, speedMode) });
  return messages;
}

export function preparePrompt(prompt, speedMode = false) {
  const trimmed = prompt.trim();
  if (!speedMode || /^\/no_think\b/i.test(trimmed)) return trimmed;
  return `/no_think ${trimmed}`;
}

export function computeBudget({
  contextTokens = defaultModelLimits.contextTokens,
  outputTokens = defaultModelLimits.recommendedOutputTokens,
  systemPrompt,
  prompt,
  history = [],
  includeHistory = true,
  speedMode = false,
  historyLimit = 8,
}) {
  const messages = buildRequestMessages({
    systemPrompt,
    prompt,
    history,
    includeHistory,
    speedMode,
    historyLimit,
  });
  const inputTokens = messages.reduce((sum, message) => sum + estimateTokens(message.content) + 4, 0);
  const safeOutputTokens = Math.max(0, Math.min(outputTokens, contextTokens));
  const totalTokens = inputTokens + safeOutputTokens;
  const remainingTokens = contextTokens - totalTokens;

  return {
    contextTokens,
    inputTokens,
    outputTokens: safeOutputTokens,
    inputLimitTokens: Math.max(0, contextTokens - safeOutputTokens),
    totalTokens,
    remainingTokens,
    isOverBudget: remainingTokens < 0,
  };
}
