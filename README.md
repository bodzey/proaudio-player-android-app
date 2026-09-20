# ProAudio Player Android

Native Android controller for ProAudio Player.

## Goals

- Native Android UI built with Kotlin and Jetpack Compose.
- Control players through the stable `/api/v1` contract exposed by `proaudio-player-native`.
- Treat multiple players as a first-class use case.
- Identify players by a persistent device/instance ID, never by IP address.
- Discover available players without subnet scanning.
- Keep discovery, firmware identity and hardware-specific behavior outside `proaudio-player-native`.
- Keep network and metering activity demand-driven so the controller adds negligible load to the player.
- Support physical appliances and independent containerized player instances through the same client model.

## Component boundary

```text
Android app
  ├─ discovery
  ├─ device registry
  ├─ player sessions
  ├─ API/event clients
  └─ native UI
         │
         │ /api/v1
         ▼
proaudio-player-native
  └─ hardware-neutral control plane

Firmware / deployment
  ├─ persistent device or instance identity
  └─ service advertisement
```

The Android app does not depend on Raspberry Pi, Buildroot, Docker, a fixed hostname or a fixed IP address.

## Device model

A player is keyed by a stable `DeviceId`. Network addresses are transient endpoints and may change at any time.

For physical appliances, the persistent ID belongs to the firmware/runtime integration layer. For container deployments, the ID belongs to the deployment instance. The Android application only consumes the identity; it does not generate identities for remote players.

## Discovery

Primary local discovery uses DNS-SD/mDNS through Android Network Service Discovery. Discovery is abstracted behind an application interface so additional mechanisms can be added without changing the UI or player session code.

No subnet scanning is used.

## Connection policy

Discovery does not open continuous control connections to every player. A full player session is established for the selected device. High-rate meter data is active only while the relevant UI is visible and the app is in the foreground.

## Development

The project targets the current stable Android toolchain. Development happens on `dev`; `main` remains release-oriented.

Architecture and implementation decisions are documented under `docs/`.
