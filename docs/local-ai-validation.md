# Zafiro local execution: implementation and device validation

This is the existing application, com.niki914.zafiro, version 1.6.0-zafiro.4 (17).
Work continues on feat/zafiro-1.6-integration, draft PR #4. PR #3 and the
checkpoint/zafiro-custom-1.5.2 branch preserve the prior custom implementation.
Neither main nor releases are modified by this work.

## Integration

Official upstream integrated: 6a23b2def5e50d8aa6677d1d9630310aa9260611.
The v12-1.6.0 release commit is a9bcb974d7713bfefb04cd9fe810320d3076c2d1.
The existing custom reference is 325ff55331bf134ecbc17835a84273df65a01c8b.
The latest upstream integration produced no Git conflicts. Its import/dead-SDK
cleanup and redundant-test removals were reviewed; new custom behavior tests
were retained. Earlier conversation history/onboarding/Skills integration is
preserved. Device UI verification is still necessary.

## Implemented real paths

- Direct commands bypass the provider configuration, Skills discovery, MCP
  discovery and all model inference. They still use the same OKIA conversation,
  cancellation, runtime tools and enabled-capability policy.
- Hebrew/English app launch, back, home, volume, Bluetooth settings and battery
  saver settings use existing Android authority. Installed app resolution is
  generic; an absent/ambiguous application is not guessed.
- A closed bilingual WhatsApp grammar builds a complete local plan before
  execution. Each step has action, semantic target, expected state, timeout and
  failure policy. It verifies the exact conversation title and composer.
- Sending requires AgentControl approval, followed by recipient/content
  revalidation. Submission is never retried. A cleared composer is described
  as submission, never verified delivery.
- Accessibility events feed ScreenBrain; reads are coalesced to 80 ms during a
  turn and 1 second in passive operation. No periodic screenshot capture or
  cloud screen streaming is introduced.
- ScreenState retains bounded ephemeral UI semantics and redacts password text.
  ScreenGraph retains bounded resource/class structure and verified transitions,
  invalidated by application version. It contains no messages/contact names.
- Existing cursor visualization surrounds real Accessibility execution;
  package-scoped semantic resolution rejects duplicate/disabled targets.
- Optional LiteRT-LM 0.17.1 command inference is host-provided, constrained to
  short JSON, grounded against the original input and unable to invoke tools.
  Cold/busy models immediately decline; warm inference has a short deadline.
- Local AI settings offer Fast Local, Balanced and Cloud Quality, optional
  pinned downloads, pause/resume, cancel/delete, Wi-Fi preference and benchmarks.
  A complete SHA-256/size check precedes atomic model installation.
- Only one opt-in CPU engine runs; it unloads after 60 seconds idle or app memory
  pressure. Deletion waits for download/benchmark cancellation and engine close.
- English/Hebrew resources and RTL behavior are retained.
- Voice transcripts enter the same local routing path. Cloud TTS is separate.
- Gemini, existing Skills/MCP, Shizuku, encrypted credentials, Glass and the
  custom-repository updater remain available.

## Model investigation, not claimed device results

| Component | Approach/candidate | Artifact size | Status |
| --- | --- | --- | --- |
| Tiny domain router | Bilingual character n-gram prototype classifier | Built-in small prototypes; no model download | Hint only; similarity is not calibrated confidence |
| Command/planner | Qwen3 0.6B dynamic INT4 LiteRT-LM | 344,671,744 bytes | Optional candidate, not declared winner |
| Hebrew command/planner | DictaLM 3.0 1.7B instruct INT4 community Android conversion | 892,583,936 bytes | Optional candidate, not declared winner |
| Screen perception | Accessibility semantic tree | No vision weights | Primary implemented perception |
| Vision fallback | LocalUiDetector seam; specialized detector investigation | No installed detector | No vision execution is claimed |

Primary references:
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/docs/api/kotlin/getting_started.md
- https://huggingface.co/litert-community/Qwen3-0.6B
- https://huggingface.co/dicta-il/DictaLM-3.0-1.7B-Instruct
- https://huggingface.co/barakplasma/dictalm-3.0-1.7b-thinking-android
- https://huggingface.co/microsoft/OmniParser

Multilingual MiniLM-style encoders and specialized UI detectors were inspected.
There is no credible on-device Hebrew/task benchmark here to justify adding a
larger permanent router or selecting a vision detector. Models are not bundled
in the APK; no model is automatically downloaded.

The device benchmark measures initialization, average/first-output latency,
sampled process PSS, native heap, decode throughput where reported and 15
command/negative fixtures. It is a small eligibility screen, not a statistically
calibrated confidence estimate or a real Android task-success measurement.
Only a perfect fixture result averaging <= 1.5 seconds keeps the engine warm.
Fast Local can then be selected. Models must be manually warmed again after
unloading; automatic background preloading is deliberately not implemented.

## Automated coverage

CI compiles the actual debug APK and runs existing JVM/Robolectric tests,
agent-runtime Python tests and legacy Python updater fixtures. It records real
JVM totals rather than reusing counts from an older build.

Added regressions cover Hebrew/English/mixed commands, unsupported compound
requests, exact recipient/content checks, approval order, duplicate targets,
ScreenGraph version/loop invalidation, event coalescing/password redaction,
vision-trigger decisions, inference timeout/cancellation, hash corruption,
range-resume correctness and low-storage rejection. Existing voice, cursor,
encryption, updater and Glass regressions remain in the suite.

The pure routing benchmark reports CI JVM microseconds and corpus results.
It does NOT measure final-STT-to-first-Android-action, CPU/GPU/battery/thermal
behavior or WhatsApp task completion. Do not describe those as measured.

## Remaining capabilities and device procedure

There is no attached Android device. These checks remain required:

1. Install only with a compatible signer; preserve the installed application's
   data. Compare package, versionCode and APK signer before updating.
2. Record phone model, Android/WhatsApp versions, RAM, font scale, language,
   accessibility/overlay permissions and selected routing mode.
3. Measure final STT callback to first actual Android action, then task completion
   excluding time spent awaiting user approval. Repeat each task 10 times.
4. Test: open WhatsApp, send a draft to a test contact, Spotify launch, Bluetooth,
   home, camera and battery-saver settings in Hebrew and English. Verify drafts
   are not overwritten, wrong conversations stop and rejection never sends.
5. Test live transcription, audible TTS, microphone availability, interruption,
   Jarvis wake behavior and speaker echo on speaker/headphones/Bluetooth.
6. Download each candidate on Wi-Fi; pause, restart the app, resume, corrupt a
   test artifact and confirm it cannot load. Run benchmarks independently.
7. Measure actual PSS/CPU/thermal/battery with models cold/warm and Glass dormant/
   active. Verify memory-pressure unloading and user-touch priority.
8. Check Hebrew RTL, mixed text, conversation pin/rename/multi-select/batch
   operations, onboarding, GitHub Skills installation and release notes.
9. Check Keystore migration and an actual compatible signed custom update using
   the Android installer; never bypass signing checks.

General local replanning, verified action-bearing ScreenGraph path reuse,
automatic command-model rewarming, trained/calibrated tiny-router selection,
an installed Android UI vision detector, real acoustic automatic barge-in,
flashlight/call/music-control fast paths and broad natural-language message
templates are not marked complete. Unsupported requests escalate to Gemini
before actions; an already-partially-executed sensitive plan stops safely.

No release signing key is configured in CI and no custom release is published.
A debug APK is a build artifact, not proof that it can update a user's installed
APK. The updater checks this fork's compatible release channel and enforces
package/version/signer validation; releases need separate authorization.
