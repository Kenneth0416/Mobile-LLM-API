# Mobile Model Options

## Device Compatibility Strategy

The app should not assume one phone forever. Model selection is now driven by
metadata on each model option:

- `backendType`: current text chat uses `litertlm_text`; future routes can use
  `litertlm_vision` or `mlkit_ocr`.
- `hardwareTarget`: `generic_cpu` runs through CPU LiteRT-LM; hardware-specific
  variants such as `qualcomm_sm8750`, `mediatek_mt6993`, or `google_tensor_g5`
  must match the detected SoC before they can be recommended.
- `minRamMb`: prevents large models from becoming defaults on low-memory phones.
- `recommendedSocModels`: lets future high-end CPUs choose stronger defaults.
- `avoidSocModels`: blocks known bad pairings.
- `performanceTier`: `fast`, `balanced`, `quality`, or `stress`.

The connected Sony XQ-BE72 / Snapdragon 888 / SM8350 is one verified profile,
not the only target. On this phone, the app still recommends the fast Qwen3
0.6B int4 model by default. Gemma 4 E2B is compatible as a quality test
candidate once downloaded, but it is not the default. Gemma 4 E4B remains a
stress-test candidate for higher-memory devices.

## Installed Text Models

| Model id | File | Size | Context | Role |
| --- | --- | ---: | ---: | --- |
| `qwen3-0.6b-mixed-int4` | `qwen3_0_6b_mixed_int4.litertlm` | 497 MB | 2048 | Fast generic CPU default |
| `qwen3-0.6b-dynamic-int8` | `Qwen3-0.6B.litertlm` | 614 MB | 4096 | Balanced generic CPU baseline |

Source: https://huggingface.co/litert-community/Qwen3-0.6B

## Added Text Candidates

| Model id | File | Size | Context | Output budget | Recommendation |
| --- | --- | ---: | ---: | ---: | --- |
| `gemma4-e2b-it` | `gemma-4-E2B-it.litertlm` | 2.41 GiB | 2048 | 256-512 | Quality test for 10 GB+ RAM; recommended on newer high-end SoCs |
| `gemma4-e4b-it` | `gemma-4-E4B-it.litertlm` | 3.41 GiB | 2048 | 128-256 | Stress test only for 16 GB+ RAM |

Gemma 4 E2B can be used by the current app because it is distributed as a
generic LiteRT-LM `.litertlm` file. E4B is also compatible at the file/runtime
level on suitable devices, but it is expected to be slow and memory-heavy on
CPU-only mobile inference.

Sources:

- https://developers.google.com/edge/litert-lm/models/gemma-4
- https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm

Download and install Gemma 4 E2B:

```bash
scripts/download_model.sh gemma4-e2b-it
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew assembleDebug
scripts/install_and_start.sh
```

The web chatbot at `http://localhost:5175/` reads `/v1/models`, so Gemma 4 will
appear as soon as the Android API is restarted. It remains disabled until the
model file is present on the phone. If a downloaded model is marked
`compatible: false` for the current device, the web bench keeps it out of the
selectable model list.

The `/v1/models` response includes `backend_type`, `hardware_target`,
`performance_tier`, `min_ram_mb`, `recommended_soc_models`, `avoid_soc_models`,
and `compatible` so external tooling can make the same decision.

## Other Runnable Text Candidates

These can be added later as text-only `.litertlm` candidates:

| Candidate | Notes |
| --- | --- |
| `litert-community/gemma-3-270m-it` | Very small Gemma candidate; may require license/auth. |
| `litert-community/Gemma3-1B-IT` | Good quality/speed middle ground; may require license/auth. |
| `litert-community/Qwen2.5-1.5B-Instruct` | Generic Q8 file around 1.52 GiB, likely slower than Qwen3 0.6B. |
| `litert-community/DeepSeek-R1-Distill-Qwen-1.5B` | Reasoning-oriented, around 1.71 GiB, useful for reasoning tests. |
| `litert-community/Falcon3-3B-Instruct` | Around 1.74 GiB q4, likely slow on CPU. |
| `litert-community/Llama-3.2-3B-Instruct` | Around 2.06 GiB q4, license and speed need care. |
| `litert-community/Qwen3-4B` | Mixed int4 exists, but likely too slow for default phone use. |
| `litert-community/DeepSeek-R1-Distill-Qwen-7B` | File exists, but not recommended for this phone. |

## OCR And VLM Path

OCR should be implemented as a separate on-device OCR route first, not by forcing
OCR through the current text chat endpoint.

Recommended first OCR backend:

- Google ML Kit Text Recognition v2
- Supports on-device Latin, Chinese, Devanagari, Japanese, and Korean recognizers
- Source: https://developers.google.com/ml-kit/vision/text-recognition/v2/android

Vision-language models need a new backend and API because the current runner only
sends text messages. A future VLM route should add image upload/base64 handling
and a separate endpoint such as `/v1/vision/chat`.

Possible VLM candidates:

- `litert-community/FastVLM-0.5B`
- Gemma 4 E2B/E4B multimodal variants through LiteRT-LM vision APIs once the app
  adds image content support

References:

- https://huggingface.co/litert-community/FastVLM-0.5B
- https://github.com/google-ai-edge/gallery
