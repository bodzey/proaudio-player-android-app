# Architecture

## Principles

1. `proaudio-player-native` is the hardware-neutral control plane.
2. The Android application is a native client of the versioned `/api/v1` contract.
3. A player is identified by a stable `DeviceId`, never by an IP address.
4. IPv4, IPv6 and future transports are replaceable endpoints of one device identity.
5. Discovery is event-driven; the application does not scan address ranges.
6. Only the selected player owns an active control session.
7. High-rate metering is demand-driven and exists only while its UI is visible.
8. Board-, DAC- and Linux-specific policy stays in firmware/native integration.
9. Feature availability is determined by `/api/v1/capabilities`, not firmware-version branching.
10. Control authorization is a separate concern from public LAN discovery.

## Layers

```text
Compose UI
    │
ViewModels / UI state
    │
Core repositories
    ├─ DeviceRepository
    ├─ PlayerSessionRepository
    └─ MeterRepository
    │
Core contracts
    ├─ DeviceDiscoverySource
    ├─ DeviceHistoryStore
    └─ PlayerApiClient
    │
Data sources
    ├─ Android NSD / DNS-SD
    ├─ Room known-device history
    ├─ OkHttp REST
    ├─ OkHttp SSE
    └─ Android Storage Access Framework
```

Dependencies point toward the core contracts. Compose code does not use `NsdManager`, Room, OkHttp, raw sockets or shell commands directly.

## Device discovery

Discovery consumes a stream of presence events:

```kotlin
interface DeviceDiscoverySource {
    fun events(): Flow<DeviceDiscoveryEvent>
}
```

The production source uses Android `NsdManager`. The advertisement contract is:

- service type: `_proaudio-player._tcp.`;
- TXT `id`: persistent canonical UUID;
- TXT `api`: positive API major version;
- TXT `name`: optional display name;
- SRV port: current control API port.

A DNS-SD service instance is only a presence identifier. `DeviceRegistryState` groups all current presences by `DeviceId` and unions their endpoints. Losing one IPv4/IPv6/interface presence does not remove the logical player while another presence remains.

Debug demo discovery marks its devices as non-persistable and cannot contaminate production device history.

## Device history

Live availability and persistence are intentionally different models.

`AvailableDevice` represents current discovery presence and is the only source used by `PlayerSessionRepository`.

Room stores `KnownDevice` history:

- stable `DeviceId`;
- display name;
- API major version;
- last observed endpoints;
- last-seen timestamp.

The device list merges live discovery with persisted history into `DeviceListEntry`. A live entry always overrides a persisted copy of the same `DeviceId`. Persisted-only entries are shown as offline and never fabricate network presence.

Room persistence is best-effort. A database failure must not stop mDNS discovery or an active player session. Android backup is disabled, so LAN endpoint history remains local to the installation.

## Player session

Selecting a `DeviceId` creates one control-plane session:

```text
DeviceId
  -> current live endpoints
  -> GET /api/v1/health
  -> validate advertised API major
  -> GET /api/v1/capabilities
  -> GET /api/v1/status
  -> GET /api/v1/events (SSE) or low-rate polling fallback
```

Endpoint resolution probes the selected device's advertised endpoints and accepts a healthy endpoint with the same API major. Endpoint-set changes rebuild the selected session without changing device identity.

All read and mutation operations are bound to an expected `DeviceId`. A command started for player A is rejected rather than being applied to player B after a rapid selection change.

## Player controls

`status.player` is transport-neutral. Playback buttons are enabled from `status.player.controls`; the Android UI does not branch on Spotify, AirPlay, DLNA or MPD implementation names.

MASTER volume/mute, logical MUSIC/ALERT mixer controls and physical output selection are separate controls:

```text
sources -> MUSIC ----\
                     -> MASTER -> selected physical output
alerts  -> ALERT ----/
```

Physical-output UI consumes only backend `AudioOutputDescriptor` values. It has no Raspberry Pi, ALSA-card-number or board-specific assumptions.

## Media

The Media section uses the stable native resources:

- `GET /library`, `POST /library/update`, `POST /library/play`;
- `GET /playlists`, `POST /playlists/load`;
- `GET /queue`, `POST /queue/play`, `POST /queue/remove`, `POST /queue/clear`.

Library, playlists and queue load independently so one failing resource does not hide the others. The client can retain a large library in state but renders at most 200 matching rows at once.

## Radio and alerts

Network radio uses the native directory and `/streams/play`. Custom stream URLs receive deterministic client-side HTTP/HTTPS safety checks; the native daemon remains authoritative and performs DNS-based SSRF validation.

Alert settings, provider testing and MP3 replacement files use the native settings/media API. Non-secret unsaved form drafts survive Android process recreation. Provider tokens are deliberately excluded from saved-instance state.

## Metering

`/api/v1/meters` is a dedicated 50 Hz SSE stream containing sample-peak, RMS and clip data for MASTER, MUSIC and ALERT.

`MeterRepository.states()` is a cold flow. The stream is collected only by the Player meter UI. Leaving the Player section or moving the application out of the active lifecycle cancels the HTTP call; the native meter hub then reaches zero receivers and stops its capture processes.

## State restoration

Only durable UI intent is restored:

- selected `DeviceId`;
- active application section;
- custom radio URL, scoped to its device;
- media-library search query, scoped to its device;
- non-secret unsaved alert form values, scoped to their device.

Network-derived status, endpoints, capabilities, output lists and mixer state are always reloaded.

## Android networking

The current application targets API 36. It therefore does not declare or request Android 17's `ACCESS_LOCAL_NETWORK` runtime permission.

When the application moves to target API 37, local-network access must be handled in the Android networking layer. The control API and discovery identity contract must not change as a consequence.

## Security

There is no production pairing/authentication contract in the current native `dev` API. Android therefore does not invent client-only authentication.

Current policy:

- debug builds may use cleartext HTTP for development firmware/Docker;
- release builds keep cleartext disabled;
- no SSH, shell execution or host administration is part of the Android path;
- once the native platform defines pairing and authenticated TLS, credentials must be scoped to `DeviceId` and stored with Android Keystore-backed storage.

Production release remains blocked on that backend security contract.
