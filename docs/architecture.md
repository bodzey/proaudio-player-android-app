# Architecture

## Principles

1. `proaudio-player-native` is an independent, hardware-neutral control plane.
2. The Android application is a client of the versioned `/api/v1` contract.
3. A player is identified by a stable device or deployment instance ID, not by an IP address.
4. Multiple players are a base use case.
5. Discovery is event-driven and must not scan address ranges.
6. High-rate telemetry is demand-driven and must stop when it is not visible.
7. Hardware identity and service advertisement belong to firmware or deployment integration.
8. Android-specific networking and permission behavior stays in the Android repository.

## Layers

```text
UI (Compose)
    │
ViewModel / UI state
    │
Repositories
    ├─ DeviceRepository
    ├─ PlayerRepository
    ├─ MeterRepository
    └─ SettingsRepository
    │
Data sources
    ├─ NSD / discovery
    ├─ Room
    ├─ DataStore
    └─ HTTP + SSE
```

Dependencies flow inward. UI code never talks directly to `NsdManager`, Room, OkHttp or raw network addresses.

## Device registry

The registry is keyed by `DeviceId`.

```text
DeviceId
display name
last known endpoint
API version
last seen
pairing/trust state
user metadata
```

The endpoint is replaceable. Rediscovery of the same `DeviceId` updates the endpoint instead of creating a new device.

## Discovery contract

The Android application consumes an abstract discovery stream:

```kotlin
interface DeviceDiscoverySource {
    fun discover(): Flow<DiscoveredDevice>
}
```

The first implementation is Android DNS-SD/mDNS through `NsdManager`. On modern Android it tracks resolved service information continuously so address and TXT-record changes are delivered without polling. Older supported Android versions use a serialized legacy resolve path. Future discovery mechanisms can be composed without changing feature UI.

The firmware/deployment advertisement contract is:

- DNS-SD service type: `_proaudio-player._tcp.`;
- TXT `id`: persistent canonical UUID for the physical device or deployment instance;
- TXT `api`: positive major version of the control API;
- TXT `name`: optional human-readable display name;
- SRV port: current control API TCP port.

The Android client treats IP addresses as transient resolution results. The DNS-SD service name is used only to track an advertisement within a discovery session; it is never the primary device identity.

## Sessions and load

Discovery alone does not establish a live control session with every discovered player.

A selected device gets one active player session. The session performs an initial status/capability read and then consumes low-rate events. Metering is a separate high-rate stream and is enabled only while a meter UI is visible in the foreground.

## Networking

Clients target `/api/v1`. Feature availability is capability-driven. Client code must not branch on firmware versions when a capability can express the distinction.

Android 17 local-network permissions are handled in the Android networking layer and do not affect the player API contract.

## Security

Discovery may be public inside the LAN, but control authorization is a separate concern. Credentials, once pairing is implemented, are stored through Android Keystore-backed storage and are scoped to a stable `DeviceId`.

No SSH, shell execution or host administration is part of the Android control path.


## Player session

Discovery never creates live HTTP sessions for every visible player. The application opens a control-plane session only for the selected `DeviceId`.

The session pipeline is:

```text
DeviceId
  -> current discovered endpoints
  -> GET /api/v1/health
  -> validate API major version
  -> GET /api/v1/capabilities
  -> GET /api/v1/status
```

Endpoint resolution probes the selected device's advertised endpoints and accepts the first healthy endpoint that reports the same API major version as DNS-SD. IPv4 and IPv6 are transport alternatives for one device identity, not separate devices. A change to the discovered endpoint set rebuilds the selected session without changing the selected `DeviceId`.

Plain HTTP is currently enabled only in the debug build because the development Docker and firmware control plane is HTTP. Release builds keep cleartext disabled until device pairing and authenticated TLS are implemented.
