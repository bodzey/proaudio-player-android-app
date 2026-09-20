# Roadmap

## Phase 0 — Bootstrap
- Create a buildable Android project on the stable Android toolchain.
- Configure Kotlin, Compose, lint, tests and CI.
- Establish package structure and dependency boundaries.
- Add a minimal application shell.

## Phase 1 — Device discovery
- Implement the discovery abstraction.
- Implement Android NSD/DNS-SD discovery.
- Handle Android 17 local-network permission and service-picker behavior.
- Model stable device identity and transient endpoints.
- Add discovery tests and fake discovery source.

## Phase 2 — Device registry
- Add Room persistence.
- Merge rediscovered endpoints by stable device ID.
- Track availability and last-seen state.
- Implement device selection and reconnect semantics.

## Phase 3 — ProAudio API client
- Model `/api/v1/health`, `capabilities`, `status` and events.
- Add HTTP error mapping and timeouts.
- Add SSE lifecycle management.
- Keep API base URLs session-scoped, never global.

## Phase 4 — Player experience
- Native Compose player screen.
- Playback controls, volume and mute.
- Source/player metadata.
- Connection and reconnect states.

## Phase 5 — Extended control
- Queue and playlists.
- Radio.
- Library.
- Mixer and output selection.
- Alert settings and test controls.

## Phase 6 — Metering
- Dedicated meter client and rendering path.
- Start/stop based on visibility and foreground lifecycle.
- Validate CPU, memory, network and player-side impact.

## Phase 7 — Pairing and security
- Define pairing contract with the player platform.
- Store device-scoped credentials securely.
- Handle trust reset and device replacement.

## Phase 8 — Production
- Accessibility and large-screen review.
- Offline and degraded-network behavior.
- Unit, integration and UI tests.
- Release signing and CI artifacts.
- Play distribution metadata and privacy review.
