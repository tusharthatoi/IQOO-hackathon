# Android OS Concepts: System Architecture & Subsystems
## Theoretical Foundation for the iQOO Wellness AI Platform

This document outlines the operating system layers traversed by the camera intelligence platform, tracing execution from user interface down to bare silicon.

---

### 1. The Full Android Stack

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Application Layer                                        │
│    - Prototype: iQOO Wellness Camera App (CameraX)          │
│    - Production: Native iQOO Camera (com.android.camera)    │
├─────────────────────────────────────────────────────────────┤
│ 2. Application Framework Layer                              │
│    - Camera2 / CameraX API (ImageReader, SurfaceTexture)    │
│    - WellnessManager API client proxy                       │
│    - SensorManager (Hardware Step Counter)                  │
├─────────────────────────────────────────────────────────────┤
│ 3. Android System Services (system_server)                  │
│    - CameraService (Native cameraserver daemon)             │
│    - WellnessManagerService (AIDL Binder endpoint)          │
│    - SensorService (sensor events demux)                    │
├─────────────────────────────────────────────────────────────┤
│ 4. Hardware Abstraction Layer (HAL)                         │
│    - Camera HAL3 (QTI Camera Provider)                      │
│    - Sensors HAL (CHRE / Context Hub)                       │
│    - Neural Networks HAL / QNN DSP HAL                      │
├─────────────────────────────────────────────────────────────┤
│ 5. Linux Kernel & Drivers                                   │
│    - V4L2 / Qualcomm Spectra ISP camera driver              │
│    - Binder IPC driver (/dev/binder)                        │
│    - Hexagon / Adreno GPU kernel drivers                    │
├─────────────────────────────────────────────────────────────┤
│ 6. Hardware (Snapdragon / MediaTek SoC)                     │
│    - CMOS Image Sensor (Sony IMX / Samsung ISOCELL)         │
│    - Spectra ISP + Hexagon NPU + Cortex-X/A CPU Cores       │
└─────────────────────────────────────────────────────────────┘
```

---

### 2. Deep Dive: Key OS Concepts Applied

#### A. Binder IPC & AIDL
- **Mechanism:** Android's high-speed Inter-Process Communication mechanism using a shared memory driver (`/dev/binder`) and thread pools.
- **Role in Platform:** The native iQOO Camera runs in an unprivileged or system app process. The `WellnessManagerService` runs inside `system_server`. All control commands and structured metadata cross this process boundary via typed AIDL interfaces (`IWellnessManager.aidl`).
- **Data Transfer Optimization:** Large media payloads (video frames) are never copied byte-for-byte over Binder. Instead, native file descriptors (`GraphicBuffer` / `HardwareBuffer`) are transferred, providing zero-copy memory mapping.

#### B. Privileged Applications vs Normal APKs
- **Third-Party APK (Our Prototype):** Installed in `/data/app/`. Runs in a sandboxed UID (`u0_aXXX`). Subject to runtime user permissions (`CAMERA`, `ACTIVITY_RECOGNITION`). Cannot modify system services or register with `ServiceManager`.
- **Privileged System App (Native Camera):** Installed in `/system/priv-app/` or `/product/priv-app/`. Granted signature-level permissions (`android.permission.BIND_SYSTEM_SERVICE`), allowing low-level hooks into OS services.

#### C. SELinux Security Boundaries
- Android enforces Mandatory Access Control (MAC) via SELinux. Every process has a security context (e.g., `u:r:untrusted_app:s0` vs `u:r:system_server:s0`).
- A normal APK cannot access `/dev/qdsp_device` or register a new system service because the SELinux policy blocks untrusted apps from doing so.
- Our dual-layer architecture respects SELinux: the prototype works fully within standard app boundaries, while the OEM blueprint provides the exact policy additions needed for system-level deployment.

#### D. Low-Power Sensor Architecture (CHRE)
- Android offloads continuous step counting to the **Context Hub Runtime Environment (CHRE)**—a dedicated ultra-low-power microcontroller separate from the main CPU.
- When the screen is off or the phone is in pocket, the main CPU sleeps while the sensor hub counts steps. Our architecture queries `Sensor.TYPE_STEP_COUNTER` to leverage this hardware offload without incurring battery penalties.
