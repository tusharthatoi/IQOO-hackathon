# iQOO Wellness AI Engine
### OEM-Ready Camera Intelligence Platform for Android & OriginOS

[![Platform](https://img.shields.io/badge/Platform-Android%2015%20(API%2035)-3DDC84.svg?logo=android)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-Decoupled%20Engine%20%2B%20OEM%20AIDL-006699.svg)](docs/ARCHITECTURE.md)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20On--Device%20Local--First-green.svg)](docs/ARCHITECTURE.md)
[![Retention](https://img.shields.io/badge/Retention-21--Day%20Rolling%20Purge-blue.svg)](docs/ARCHITECTURE.md)

---

## 1. Overview & Vision

The **iQOO Wellness AI Engine** transforms the smartphone camera into an on-device perception engine. Built specifically for high-performance iQOO devices, it delivers real-time visual wellness intelligence across three core domains:

1. **Food Nutrition Intelligence:** Instant on-device dish recognition and personalized portion retrieval (Structured RAG).
2. **Real-Time Posture Correction:** Single-model pose tracking with explainable geometric joint angles and state machines across 5 key exercises (Squat, Push-up, Bicep Curl, Lunge, Shoulder Press).
3. **Walking & Activity Intelligence:** Low-power hardware step tracking with rolling 21-day on-device storage.

All processing occurs strictly **on-device**; no camera frames, biometric landmarks, or nutritional data ever leave the phone.

---

## 2. Product Positioning & Hackathon Differentiation

Unlike standard wellness apps, calorie trackers, or conversational yoga assistants:
- **Camera-Native Intelligence:** The camera viewfinder IS the interface. Information is projected directly onto the user's real-time physical context via an augmented HUD.
- **Personalized Visual Nutrition (Hero Feature):** The engine learns the user's typical portions (e.g., *Biryani ~250g*) via local Room/SQLite structured retrieval. The base vision model remains fixed, while the output personalizes from historical interaction.
- **Cascaded AI Inference:** A lightweight scene classifier routes frames to either Food or Pose processing, preventing heavy models from running simultaneously. This preserves battery and keeps thermal throttling to a minimum.
- **Honest OEM Architecture:** The prototype is a deployable Android app built with CameraX; an isolated `future-oem/` module provides production-ready AIDL contracts and system service blueprints for future OriginOS native camera integration.

---

## 3. Dual-Layer Architecture

```
PROTOTYPE LAYER (Deployable APK):
CameraX Viewfinder (app) ──► WellnessManager ──► WellnessEngine ──► Local Room DB

FUTURE OEM LAYER (OriginOS Platform Target):
Native iQOO Camera ──► Binder IPC (IWellnessManager.aidl) ──► WellnessManagerService (system_server) ──► WellnessEngine
```

---

## 4. Repository Structure

```text
iqoo-wellness/
├── app/                  # CameraX camera application & visual HUD overlay
├── wellness-engine/      # Decoupled core library (scene, food, posture, activity, storage)
├── future-oem/           # Reference OEM AIDL interface & system service implementation
├── data/                 # Seed nutrition database (offline foods & macros)
├── docs/                 # Architecture, OEM integration, OS concepts & test plans
└── README.md
```

---

## 5. Documentation Directory

- **[docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md):** 12-phase vertical engineering roadmap.
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md):** Detailed system design, data flow diagrams, and cascaded inference mechanics.
- **[docs/OEM_INTEGRATION.md](docs/OEM_INTEGRATION.md):** Blueprint for OriginOS `system_server` integration, SELinux policy, and zero-copy hardware buffers.
- **[docs/ANDROID_OS_CONCEPTS.md](docs/ANDROID_OS_CONCEPTS.md):** In-depth exploration of Binder, AIDL, CHRE sensor hubs, and Android security boundaries.
- **[docs/TEST_PLAN.md](docs/TEST_PLAN.md):** Deterministic unit testing and validation matrix.
