# Mobile Model Speed Results

## 2026-06-25 Sony XQ-BE72 / Snapdragon 888

Device:

- Model: Sony XQ-BE72
- SoC: SM8350 / Snapdragon 888
- RAM reported by app: 11176 MB
- Runtime: LiteRT-LM CPU backend
- API: `http://127.0.0.1:18080/v1/chat/completions`

Method:

- All catalog `.litertlm` models were downloaded and pushed to the phone.
- Each model ran one warmup request first so the measured request was mostly
  generation time after model initialization.
- Prompt: `/no_think 请用中文用两句话说明手机本地大模型的优点和限制。`
- Request parameters: `temperature=0.2`, `top_k=1`, `top_p=0.8`, `max_tokens=96`
- Note: the current LiteRT-LM runner accepts `max_tokens` in the API request, but
  does not yet pass it into a generation stop condition. Token counts below are
  the API-reported rough token estimates for the actual returned text.

| Model id | Tier | Compatible | Warmup | Measured elapsed | Completion tokens | Speed |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| `qwen3-0.6b-mixed-int4` | fast | yes | 2675 ms | 1817 ms | 33 | 18.16 tok/s |
| `qwen3-0.6b-dynamic-int8` | balanced | yes | 4594 ms | 7998 ms | 50 | 6.25 tok/s |
| `gemma4-e2b-it` | quality | yes | 9258 ms | 22228 ms | 81 | 3.64 tok/s |
| `gemma4-e4b-it` | stress | no | 19982 ms | 32282 ms | 63 | 1.95 tok/s |

Current conclusion:

- `qwen3-0.6b-mixed-int4` is the best default for interactive speed on this
  phone.
- `qwen3-0.6b-dynamic-int8` is usable when longer context or a quality baseline
  matters more than speed.
- `gemma4-e2b-it` runs successfully and is useful for quality testing, but it is
  noticeably slower.
- `gemma4-e4b-it` also runs, but it is a stress model on this Snapdragon 888
  phone and should not be the default.
