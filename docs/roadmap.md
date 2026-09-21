# Roadmap

This file tracks implementation state in `dev`. “Implemented” means the code path exists and has targeted tests; it does not replace a successful full Gradle/CI verification or device runtime validation.

## Phase 0 — Bootstrap

Implemented:
- Android application project and native Compose shell.
- Kotlin/Compose/AGP configuration.
- lint and unit-test tasks.
- GitHub Actions workflow.

Validation remaining:
- CI runner currently fails before checkout, so the latest branch still needs a complete `testDebugUnitTest assembleDebug lintDebug assembleRelease` pass.

## Phase 1 — Device discovery

Implemented:
- `DeviceDiscoverySource` event abstraction.
- Android NSD/DNS-SD source.
- stable `DeviceId` plus transient multi-endpoint model.
- TXT `id/api/name` parsing and tests.
- debug-only fake discovery source.
- multiple presences aggregated into one logical player.

Android 17:
- targetSdk remains 36 intentionally.
- local-network runtime permission handling must be added when targetSdk moves to 37.

## Phase 2 — Device registry

Implemented:
- Room 2.8 known-device persistence behind `DeviceHistoryStore`.
- live and persisted/offline device models kept separate.
- endpoint history, last-seen state and API version persistence.
- stable-ID merge semantics.
- selected-device reconnect semantics.
- debug demo devices excluded from persistence.
- database failures isolated from live discovery/session behavior.

Remaining:
- generate and commit the Room v1 schema through a successful build.
- add migration tests when database version first changes.

## Phase 3 — ProAudio API client

Implemented:
- health, capabilities and status.
- typed HTTP errors and bounded timeouts.
- low-rate status SSE with polling fallback.
- session-scoped endpoint selection.
- device-bound mutations to prevent cross-device races.
- generic cancellation-safe SSE transport.

## Phase 4 — Player experience

Implemented:
- native Compose player.
- transport-neutral play/pause/stop/previous/next controls.
- capability-driven control enablement.
- MASTER volume and mute.
- source metadata and progress.
- reconnect/offline states.
- adaptive compact/wide layout.
- process restoration of selected player and section.

## Phase 5 — Extended control

Implemented:
- radio catalog and custom HTTP/HTTPS streams.
- queue.
- playlists.
- local library and MPD index refresh.
- MUSIC/ALERT logical mixer.
- physical audio-output selection.
- alert provider/audio settings.
- alert provider test action.
- alert MP3 upload/reset through Storage Access Framework.

Remaining:
- real-device/runtime validation of each mutation against current native `dev`.
- broader UI/accessibility instrumentation tests.

## Phase 6 — Metering

Implemented:
- typed `/api/v1/meters` client.
- 50 Hz SSE parser.
- demand-driven `MeterRepository`.
- isolated MASTER/MUSIC/ALERT stereo meter UI.
- peak/RMS/clip rendering.
- lifecycle cancellation when meter UI is not visible.

Remaining:
- runtime CPU/memory/network measurement on representative player hardware and phones.

## Phase 7 — Pairing and security

Blocked on native platform contract.

Required:
- define pairing protocol and trust lifecycle.
- authenticated TLS for control API.
- device-scoped Android Keystore-backed credentials.
- trust reset/device replacement flow.

Until then:
- local installations use HTTP because the native API does not yet expose an
  agreed TLS and pairing contract;
- the application does not transmit credentials or invent client-only
  authentication;
- authenticated TLS remains a production-release blocker.

## Phase 8 — Production

Partially implemented:
- adaptive layout.
- minimum touch targets and semantics on primary controls.
- offline known-device history.
- state restoration.
- least-privilege manifest and disabled Android backup.

Remaining:
- full Gradle/lint/release build after CI runner recovery.
- device/runtime regression matrix.
- UI instrumentation and accessibility review.
- release signing.
- CI artifacts.
- Play distribution metadata/privacy review.
- target API 37 local-network permission migration when the project adopts it.
