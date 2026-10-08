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
shown in Local AI as an experimental candidate with explicit unavailable status.
Download and visual routing remain unimplemented until a supported vision
backend, model/vision artifact hashes and device validation are available.

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
