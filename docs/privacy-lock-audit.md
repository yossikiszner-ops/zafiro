# Zafiro managed network privacy audit

Scope: Android application sources, manifests, direct Gradle dependencies, provider configuration, shared OkHttp, OKIA transport/MCP, and local script integration on the clean Glass branch.

## Implemented protections

- Managed AI and MCP traffic shares RoutingRuntime/HttpEngine. Its default-on origin allowlist is derived from the selected configured provider and enabled HTTP MCP integrations. Origins include scheme and non-default port; similar hostnames do not match. Redirect following is disabled to prevent destination changes outside this boundary.
- Voice uses the official Gemini origin only, with an explicitly configured Google credential. It disables redirects, sends completed WAV utterances for transcription, and sends exact reply chunks plus minimal delivery instructions for speech. It does not send conversation history to TTS or save recordings.
- Microphone consent uses the existing PermissionManager system dialog/settings flow. It never uses root or Shizuku to silently grant microphone access. Recording stops when the conversation screen leaves the foreground or after 30 seconds idle.
- Local executable tools require the existing human approval UI while the lock is enabled. Approved scripts are executable code and can use independent network clients; this policy is not a sandbox or device firewall.
- Android backup is disabled to reduce accidental export of provider credentials and local conversation/memory data. The existing configuration store now encrypts provider configurations, legacy local settings and MCP server documents using AES-256-GCM with an Android Keystore key. The store ID is authenticated to prevent swapping ciphertext between domains. Valid plaintext documents are migrated atomically on read; a key or authentication failure never falls back to writing plaintext. Hardware-backed key storage is not guaranteed on every device.
- The voice settings screen shows allowed managed destinations and the scope/limitations of this policy. Disabling the policy is explicit and persisted locally.

## Existing network and permission surfaces

- INTERNET, overlay, special-use foreground service, legacy storage, media access, all-files storage, and RECORD_AUDIO are declared. Storage, Shizuku/root, accessibility, overlay and approvals retain their existing feature purposes and central permission handling.
- ProviderSpec contains public official endpoints for supported providers. Only the selected configured provider is used by the agent; there is no added analytics upload destination.
- UpdateCheckApi separately accesses api.github.com/repos/niki914/zafiro/releases. Browser/open_uri actions delegate to Android applications. These flows are outside managed AI/MCP traffic and are not represented as blocked by this policy.
- SharedHttp is also used by other application features; it is not globally replaced. User-approved local scripts/Skills, external apps, Python package installation, root/Shizuku processes, DNS, and build dependency downloads can have independent network access.
- Cleartext traffic remains allowed in the manifest for intentionally configured local HTTP providers/MCP servers. The allowlist still requires their exact configured origin. Users should use HTTPS for remote services.
- Direct source/dependency review found no declared Firebase Analytics/Crashlytics, Sentry, Bugly or other obvious analytics SDK. This is evidence from declared code, not proof that every transitive native/library dependency has no network behavior. No packet capture or device-wide runtime traffic audit has been performed.
- WebRTC VAD is a local native dependency, not a cloud speech service. Unsupported native ABIs fall back to local energy detection. Actual microphone/echo quality requires device verification.

## Verification and remaining work

Exact origin/scheme/port policy, permission consent chains, tool projection, context boundaries, model discovery selection, PCM/WAV processing and hierarchical playback cancellation have JVM tests. Android CI builds the integrated app and runs the repository tests.

A full device firewall, per-integration operation-level permission grants, packet capture, continuous background microphone, and signed release distribution are not implemented by this milestone. Do not describe this policy as blocking all phone traffic or arbitrary approved scripts.
