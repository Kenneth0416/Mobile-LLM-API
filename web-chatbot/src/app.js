import {
  averageThroughput,
  buildRequestMessages,
  computeBudget,
  computeRunMetrics,
  defaultModelLimits,
  modelLimitsFromModel,
  modelSummaryParts,
  normalizeModelList,
  qualityNotes,
} from "./metrics.js";

const state = {
  samples: [],
  messages: [],
  models: [],
  selectedModelId: "",
  modelLimits: { ...defaultModelLimits },
  isBusy: false,
};

const els = {
  statusDot: document.querySelector("#statusDot"),
  statusText: document.querySelector("#statusText"),
  latency: document.querySelector("#latency"),
  tokens: document.querySelector("#tokens"),
  throughput: document.querySelector("#throughput"),
  average: document.querySelector("#average"),
  budgetPanel: document.querySelector("#budgetPanel"),
  contextTokens: document.querySelector("#contextTokens"),
  inputTokens: document.querySelector("#inputTokens"),
  outputBudget: document.querySelector("#outputBudget"),
  remainingTokens: document.querySelector("#remainingTokens"),
  endpoint: document.querySelector("#endpoint"),
  modelSelect: document.querySelector("#modelSelect"),
  modelMeta: document.querySelector("#modelMeta"),
  systemPrompt: document.querySelector("#systemPrompt"),
  speedMode: document.querySelector("#speedMode"),
  temperature: document.querySelector("#temperature"),
  maxTokens: document.querySelector("#maxTokens"),
  runBench: document.querySelector("#runBench"),
  clearChat: document.querySelector("#clearChat"),
  messages: document.querySelector("#messages"),
  form: document.querySelector("#chatForm"),
  prompt: document.querySelector("#prompt"),
  send: document.querySelector("#send"),
};

const benchPrompts = [
  "用三句话解释这个本地手机模型适合做什么。",
  "写一个简短的 Kotlin 函数，返回两个整数的和。",
  "用中文列出三条判断 LLM 输出质量的标准。",
];

els.form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const prompt = els.prompt.value.trim();
  if (!prompt) return;
  els.prompt.value = "";
  await runPrompt(prompt);
});

els.runBench.addEventListener("click", async () => {
  setBusy(true);
  try {
    for (const prompt of benchPrompts) {
      await runPrompt(prompt, { includeHistory: false, manageBusy: false });
    }
    setStatus("ready", "基准测试完成");
  } finally {
    setBusy(false);
  }
});

els.clearChat.addEventListener("click", () => {
  state.samples = [];
  state.messages = [];
  renderMessages();
  renderMetrics();
  renderBudget();
});

for (const element of [els.systemPrompt, els.prompt, els.maxTokens, els.speedMode]) {
  element.addEventListener("input", () => renderBudget());
  element.addEventListener("change", () => renderBudget());
}

els.modelSelect.addEventListener("change", () => {
  state.selectedModelId = els.modelSelect.value;
  applyModelLimits(selectedModel());
  renderModelMeta();
});

loadModels();
checkHealth();
renderBudget();

async function loadModels() {
  const modelsUrl = els.endpoint.value.replace(/\/v1\/chat\/completions$/, "/v1/models");
  try {
    const response = await fetch(modelsUrl);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    applyModelList(payload);
  } catch (error) {
    els.modelMeta.textContent = `模型列表加载失败：${error.message}`;
  }
}

async function checkHealth() {
  const healthUrl = els.endpoint.value.replace(/\/v1\/chat\/completions$/, "/health");
  try {
    const response = await fetch(healthUrl);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const health = await response.json();
    if (!state.selectedModelId) {
      applyModelLimits(health.active_model);
    }
    setStatus(health.runner?.ready ? "ready" : "busy", health.runner?.message || "已连接");
  } catch (error) {
    setStatus("error", `连接失败：${error.message}`);
  }
}

async function runPrompt(prompt, { includeHistory = true, manageBusy = true } = {}) {
  const history = state.messages.slice();
  const budget = currentBudget(prompt, includeHistory, history);
  if (budget.isOverBudget) {
    renderBudget(budget);
    setStatus("error", `上下文超出 ${budget.contextTokens} token，请缩短输入或降低输出预算。`);
    appendMessage({ role: "error", text: "输入 + 预留输出超过当前模型上下文窗口。" });
    return;
  }

  if (manageBusy) setBusy(true);
  appendMessage({ role: "user", text: prompt });

  const requestMessages = buildRequestMessages({
    systemPrompt: els.systemPrompt.value,
    prompt,
    history,
    includeHistory,
    speedMode: els.speedMode.checked,
  });
  const speedMode = els.speedMode.checked;
  const outputTokens = currentOutputTokens();
  const model = selectedModel();
  if (!model?.installed) {
    setStatus("error", "请选择已安装的模型。");
    appendMessage({ role: "error", text: "当前模型未安装到手机，不能发送请求。" });
    return;
  }

  const startedAt = performance.now();
  try {
    const response = await fetch(els.endpoint.value, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        model: state.selectedModelId,
        messages: requestMessages,
        temperature: Number(els.temperature.value),
        top_k: speedMode ? 1 : 40,
        top_p: speedMode ? 0.8 : 0.95,
        max_tokens: outputTokens,
      }),
    });
    const payload = await response.json();
    if (!response.ok) {
      throw new Error(payload.error?.message || `HTTP ${response.status}`);
    }
    const endedAt = performance.now();
    const rawText = payload.choices?.[0]?.message?.content || "";
    const quality = qualityNotes(rawText);
    const metrics = computeRunMetrics({
      startedAt,
      endedAt,
      text: rawText,
      usage: payload.usage,
    });
    state.samples.push(metrics);
    appendMessage({
      role: "assistant",
      text: rawText,
      visibleText: quality.visible,
      notes: quality.notes,
      metrics,
    });
    renderMetrics(metrics);
    setStatus("ready", "模型已响应");
  } catch (error) {
    appendMessage({ role: "error", text: error.message });
    setStatus("error", error.message);
  } finally {
    if (manageBusy) setBusy(false);
    renderBudget();
  }
}

function appendMessage(message) {
  state.messages.push(message);
  renderMessages();
}

function renderMessages() {
  els.messages.innerHTML = "";
  for (const message of state.messages) {
    const node = document.createElement("article");
    node.className = `message ${message.role}`;
    const meta = document.createElement("span");
    meta.className = "meta";
    meta.textContent = labelFor(message);
    node.append(meta);
    node.append(document.createTextNode(message.visibleText || message.text));
    if (message.notes || message.metrics) {
      const quality = document.createElement("div");
      quality.className = "quality";
      const speed = message.metrics
        ? `耗时 ${formatMs(message.metrics.elapsedMs)} · ${message.metrics.completionTokens} token · ${message.metrics.tokensPerSecond.toFixed(2)} tok/s`
        : "";
      quality.textContent = [speed, ...(message.notes || [])].filter(Boolean).join(" · ");
      node.append(quality);
    }
    els.messages.append(node);
  }
  els.messages.scrollTop = els.messages.scrollHeight;
}

function renderMetrics(latest = null) {
  els.latency.textContent = latest ? formatMs(latest.elapsedMs) : "-";
  els.tokens.textContent = latest ? `${latest.completionTokens}` : "-";
  els.throughput.textContent = latest ? `${latest.tokensPerSecond.toFixed(2)}` : "-";
  const average = averageThroughput(state.samples);
  els.average.textContent = average ? `${average.toFixed(2)} tok/s` : "-";
}

function renderBudget(budget = currentBudget()) {
  els.contextTokens.textContent = `${budget.contextTokens}`;
  els.inputTokens.textContent = `${budget.inputTokens}`;
  els.outputBudget.textContent = `${budget.outputTokens}`;
  els.remainingTokens.textContent = `${budget.remainingTokens}`;
  els.budgetPanel.classList.toggle("over", budget.isOverBudget);
  updateActions(budget);
}

function currentBudget(prompt = els.prompt.value.trim(), includeHistory = true, history = state.messages) {
  return computeBudget({
    ...state.modelLimits,
    outputTokens: currentOutputTokens(),
    systemPrompt: els.systemPrompt.value,
    prompt,
    history,
    includeHistory,
    speedMode: els.speedMode.checked,
  });
}

function currentOutputTokens() {
  const requested = Number(els.maxTokens.value) || state.modelLimits.recommendedOutputTokens;
  return Math.max(0, Math.min(requested, state.modelLimits.maxOutputTokens));
}

function applyModelLimits(model) {
  if (!model) return;
  state.modelLimits = modelLimitsFromModel(model);
  els.maxTokens.max = `${state.modelLimits.maxOutputTokens}`;
  if (Number(els.maxTokens.value) > state.modelLimits.maxOutputTokens) {
    els.maxTokens.value = `${state.modelLimits.maxOutputTokens}`;
  }
  renderBudget();
}

function applyModelList(payload) {
  const normalized = normalizeModelList(payload);
  state.models = normalized.models;
  const currentStillInstalled = state.models.some(
    (model) => model.id === state.selectedModelId && model.installed,
  );
  state.selectedModelId = currentStillInstalled ? state.selectedModelId : normalized.selectedModelId;
  renderModelOptions();
  applyModelLimits(selectedModel());
  renderModelMeta();
}

function renderModelOptions() {
  els.modelSelect.innerHTML = "";
  for (const model of state.models) {
    const option = document.createElement("option");
    option.value = model.id;
    option.disabled = !model.installed;
    const stateLabel = !model.installed
      ? "（未安装）"
      : model.compatible === false
        ? "（当前设备不推荐）"
        : "";
    option.textContent = `${model.display_name || model.id}${stateLabel}`;
    if (model.id === state.selectedModelId) {
      option.selected = true;
    }
    els.modelSelect.append(option);
  }
}

function renderModelMeta() {
  const model = selectedModel();
  if (!model) {
    els.modelMeta.textContent = "没有可用模型。";
    return;
  }
  els.modelMeta.textContent = [
    ...modelSummaryParts(model, state.modelLimits),
    `${formatBytes(model.size_bytes)}`,
  ].join(" · ");
}

function selectedModel() {
  return state.models.find((model) => model.id === state.selectedModelId) || null;
}

function hasSelectableModel() {
  const model = selectedModel();
  return Boolean(model?.installed);
}

function labelFor(message) {
  if (message.role === "user") return "你";
  if (message.role === "assistant") return "手机 LLM";
  return "错误";
}

function formatMs(ms) {
  if (ms >= 1000) return `${(ms / 1000).toFixed(2)}s`;
  return `${Math.round(ms)}ms`;
}

function setBusy(isBusy) {
  state.isBusy = isBusy;
  updateActions();
  if (isBusy) setStatus("busy", "模型生成中");
}

function updateActions(budget = currentBudget()) {
  els.send.disabled = state.isBusy || budget.isOverBudget || !hasSelectableModel();
  els.runBench.disabled = state.isBusy || !hasSelectableModel();
}

function setStatus(kind, text) {
  els.statusDot.className = `dot ${kind}`;
  els.statusText.textContent = text;
}

function formatBytes(bytes) {
  const value = Number(bytes) || 0;
  if (!value) return "size unknown";
  if (value >= 1024 * 1024 * 1024) return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`;
  return `${Math.round(value / 1024 / 1024)} MB`;
}
