# GUI-Owl candidate: integration status

This extends the existing Local AI work on `feat/zafiro-1.6-integration`.
It does not introduce another agent, replace the current planner or change routing.

## Verified upstream facts

- Official model: https://huggingface.co/mPLUG/GUI-Owl-1.5-2B-Instruct
- Architecture: Qwen3-VL, MIT license; official deployment example uses vLLM.
- Official AndroidWorld score: 67.9. This is task success on that benchmark,
  not phone inference speed, Hebrew accuracy or a Zafiro success rate.
- Quantized GGUF candidates exist, but availability does not establish Android
  runtime compatibility. Image inference also needs compatible vision weights.

## Blocking compatibility gap

Zafiro's `LocalCommandRuntime` currently loads LiteRT-LM files and supplies only
text. Do not put a GGUF into `ModelArtifact.candidates`, rename it `.litertlm`,
or report the model as installed/working after downloading it. The model is
shown in Local AI as an optional experimental visual model.
The optional native adapter now uses pinned llama.cpp/libmtmd, not the LiteRT
text loader. Both Q4_K_M language weights (1,107,410,432 bytes) and Q8_0 vision
weights (445,053,216 bytes) have pinned revision and SHA-256 checks. Settings
support bundle download/pause/delete, opt-in enable and a screenshot smoke test.
The existing screen-read tool requests advisory visual controls only when the
accessibility tree is root-only/empty. Screen changes discard observations.
This is perception integration, not calibrated coordinate authority or proof of
GUI task success. Native/Android build and physical-device inference still need validation.

## Existing benchmark extension

`AndroidBrainBenchmark` supplies a common bilingual device corpus and measurement
contract for accessibility, Qwen3 0.6B, DictaLM and GUI-Owl. It covers screenshot
grounding, tap/type/swipe planning, multiple steps, unexpected-screen recovery,
and accessibility plus screenshots. The runner is a sequential evaluation hook,
not an agent or an executor. No device results have been collected.

Use the same device, screen fixtures, instructions and externally verified final
states for all candidates; report unsupported cases rather than silently omitting
them. Execute with the existing permission and action validation path. Record
cold-load, first output and completion latency, peak process PSS, all artifact
storage, battery charge-counter delta when supported, failures and task success.
Use repeated warm runs, medians and p95; battery comparisons need long enough
paired sessions, comparable brightness/temperature and disconnected charging.
Never turn missing charge-counter support into zero consumption. Record model,
quantization, runtime revision, phone, OS, input mode and screen resolution.

## Device target and outstanding validation

User confirmed Samsung Galaxy A25 5G. RAM capacity has not been supplied.
Requested tasks include opening apps, WhatsApp messaging, form filling and
unexpected-screen recovery in Hebrew. No phone is connected to this environment.
The native adapter uses two CPU threads, a 2048-token context and at most 512
image tokens with a 768-pixel maximum image edge. It checks available RAM before
loading, unloads the small text model first, bounds decoding and idles out after
30 seconds. A snapshot smoke test does not measure multi-step task completion.
Battery charge-counter deltas on a single inference are noisy and are reported
only as raw observations; they are not a battery-efficiency ranking.

## Required next implementation

1. Validate a maintained ARM64 Qwen3-VL backend with real screenshot inference.
2. Pin and verify both quantized language and vision artifacts; support pause,
   resume, deletion, low-memory handling and explicit opt-in using existing settings.
3. Attach the adapter to the existing runtime after deterministic commands, tiny
   router, accessibility and small planner have failed to resolve a visual task.
4. Validate coordinates against the current screen revision before execution;
   discard predictions after screen changes. Preserve existing approvals.
5. Run the common device corpus with human/oracle outcome checks. Only then decide
   whether to replace/combine layers or keep GUI-Owl for difficult screens.
6. Keep FastLocal cloud prohibition in force; GUI-Owl failure cannot authorize Gemini.

No APK contains GUI-Owl weights. No performance or Hebrew claim is inferred from
model size, quantization availability, unit tests or upstream benchmark scores.
