# iQOO Wellness AI Engine — Implementation Plan (Revised v2.0)
## OEM-Ready Camera Intelligence Platform — Realignment & Product Positioning

**Target Event:** iQOO Hackathon 2026  
**Target Architecture:** Android 15 (API 35, minSdk 26) / iQOO / OriginOS-Compatible  
**Status:** Approved Architectural Blueprint & Pre-Implementation Specification  

---

### 1. Executive Summary & Product Repositioning

#### What This Project IS:
A **camera-native wellness intelligence layer** that transforms the smartphone camera into an on-device perception engine. It understands food nutrition, exercise posture, and daily physical activity while **learning the user's personal behavioral patterns locally on-device through structured retrieval**.

#### What This Project IS NOT:
- **NOT** a generic fitness app (no workout social feeds, leaderboards, or communities).
- **NOT** a manual calorie diary (no tedious multi-step manual search logs).
- **NOT** a yoga voice coach (differentiating strictly from vision+voice conversational assistants shown in prior hackathons).
- **NOT** an imaginary OriginOS hack (no fake claims of injecting private camera binaries or tampering with Android system partitions without OEM platform keys).

#### Key Product Differentiation:
1. **Camera-Native Intelligence:** The camera viewfinder IS the interface. Real-time visual intelligence HUD overlays context directly onto the scene.
2. **Personalized Visual Nutrition (Hero Feature):** Food AI identifies dishes; the engine retrieves user-specific history (e.g., typical portion = 250g for Biryani) via local Room/SQLite structured retrieval. Personalization improves without modifying base vision weights.
3. **Deterministic & Explainable Posture Correction:** Single pose model with geometric joint angle calculations and finite state machines across 5 key exercises (Squat, Push-up, Bicep Curl, Lunge, Shoulder Press). No generative LLM hallucinating physical joint angles.
4. **Cascaded AI Inference:** Lightweight scene classifier routes frames to either Food or Pose pipeline, never running heavy models concurrently, protecting iQOO battery and thermals.
5. **Architectural Honesty:** Working CameraX prototype today; zero-compromise AIDL/Binder IPC bridge in `future-oem/` for native OriginOS camera integration tomorrow.

---

### 2. Dual-Layer Architecture: Prototype vs Future OEM

```
+---------------------------------------------------------------------------------------------------+
| LAYER A: CURRENT WORKING PROTOTYPE (Deployable Standalone APK on iQOO Hardware)                  |
+---------------------------------------------------------------------------------------------------+
                                        iQOO Physical Device
                                                 │
                                                 ▼
                                     CameraX Preview & Analysis
                                 (KEEP_ONLY_LATEST Backpressure)
                                                 │
                                                 ▼
                                     WellnessManager (Facade)
                                                 │
                                                 ▼
                                     WellnessEngine (Core AI)
                                                 │
                   ┌─────────────────────────────┼─────────────────────────────┐
                   ▼                             ▼                             ▼
             Food Pipeline                Posture Pipeline              Activity Engine
         (Classifier + Seed DB)         (Pose + Joint Math)          (Step Sensor Listener)
                   │                             │                             │
                   ▼                             │                             ▼
          Structured RAG Retrieval               │                    21-Day Local Retention
         (Learned Typical Portions)              │                      (Auto-Purge Worker)
                   │                             │                             │
                   └─────────────────────────────┼─────────────────────────────┘
                                                 ▼
                                     Local Room SQLite Database
                                     (100% On-Device, No Cloud)

+---------------------------------------------------------------------------------------------------+
| LAYER B: FUTURE OEM ARCHITECTURE (Reference Design for Vivo/iQOO OriginOS Platform Integration)   |
+---------------------------------------------------------------------------------------------------+
                                     Native iQOO Camera App
                                 (com.android.camera / OriginOS)
                                                 │
                                                 ▼
                               Official OEM Camera Framework Hook
                                                 │
                                                 ▼
                             WellnessManager Client SDK (android.os)
                                                 │
                                                 ▼ [Binder IPC / AIDL Transaction]
                                     IWellnessManager.aidl
                                                 │
                                                 ▼
                         WellnessManagerService (Android system_server)
                             (Privileged OEM SystemService Lifecycle)
                                                 │
                                                 ▼
                                           WellnessEngine
```

---

### 3. Feature Priorities & Pipelines

#### Priority 1 (Hero Feature): Food Recognition + Local Personalization
- **Camera Frame Input:** Direct YUV/RGBA frame passed from camera analysis.
- **Classification & Localization:** On-device visual classification identifying food items (including diverse regional and Indian dishes like Biryani, Dosa, Paneer Tikka, Roti, Dal).
- **Portion Estimation:** Default portion size heuristic based on bounding area and depth estimation.
- **Structured RAG (Room Retrieval):**
  - **First Encounter:** System identifies "Biryani", defaults to 250g. User adjusts or confirms portion, ingredients, or preparation method.
  - **Subsequent Scans:** System performs fast local SQLite retrieval of user history (typical portion, ingredient quantities, cooking/preparation method, oil/fat usage, ingredient substitutions, user corrections):
    ```sql
    SELECT typical_grams, ingredient_context, preparation_method FROM portion_history WHERE food_name = 'Biryani' ORDER BY timestamp DESC LIMIT 5
    ```
  - Displays: *"Recognized Biryani — Your typical portion is ~250g (Pressure-cooked, moderate oil: 380 kcal, 14g protein)"*.
  - Base model weights remain static; intelligence emerges from historical user context without model retraining.
- **Local Nutrition Database:** Seeded offline database storing calories, proteins, carbohydrates, fats, and dietary fiber per 100g.

#### Priority 2: Real-Time Explainable Posture Correction
- **Single Model Architecture:** Single lightweight pose estimator (17 body landmarks). Avoid multiple heavy models.
- **Geometric Joint Angle Engine:**
  - Knee Angle: $\angle(\text{Hip}, \text{Knee}, \text{Ankle})$
  - Hip Angle: $\angle(\text{Shoulder}, \text{Hip}, \text{Knee})$
  - Elbow Angle: $\angle(\text{Shoulder}, \text{Elbow}, \text{Wrist})$
  - Shoulder Angle: $\angle(\text{Elbow}, \text{Shoulder}, \text{Hip})$
- **Exercise State Machines (5 Target Exercises):**
  1. **Squat:** Setup $\rightarrow$ Descent $\rightarrow$ Inflection (Depth check: Knee $< 90^\circ$, Hip crease below knee) $\rightarrow$ Ascent $\rightarrow$ Rep Complete.
  2. **Push-Up:** Plank check $\rightarrow$ Descent (Elbow $\approx 90^\circ$, neutral spine) $\rightarrow$ Press $\rightarrow$ Lockout.
  3. **Bicep Curl:** Starting hang $\rightarrow$ Flexion (Elbow flexion $< 60^\circ$, elbow pin check) $\rightarrow$ Extension $\rightarrow$ Rep.
  4. **Lunge:** Stride stance $\rightarrow$ Drop (Front knee $90^\circ$, rear knee hover) $\rightarrow$ Drive up.
  5. **Shoulder Press:** Rack position $\rightarrow$ Overhead extension (Full elbow lockout, zero excessive arch) $\rightarrow$ Lower.
- **Explainable Feedback:** Explicit rule violations (e.g., *"Knees caving inward"*, *"Squat deeper — hips above knees"*, *"Keep elbows stationary"*).

#### Priority 3: Walking & Activity Intelligence
- **Low-Power Sensor:** Hardware step counter (`Sensor.TYPE_STEP_COUNTER` with fallback `TYPE_STEP_DETECTOR`).
- **Computed Metrics:** Steps, estimated active calories burned (MET formula based on cadence), temporary walking distance and speed.
- **Strict 21-Day Retention:**
  - Persist strictly `daily_steps` and `daily_calories`.
  - Temporary session distance/speed computed dynamically and discarded.
  - Room DAO / WorkManager triggers automated cleanup:
    ```sql
    DELETE FROM daily_activity WHERE date < date('now', '-21 days')
    ```

#### Priority 4: Cascaded Scene Inference
- **Thermal & Battery Protection:**
  - Frame rate throttled to 5–10 FPS for scene classification.
  - Scene Router determines scene: `FOOD_SCENE` $\rightarrow$ activates Food Pipeline; `EXERCISE_SCENE` $\rightarrow$ activates Pose Pipeline; `NORMAL_SCENE` $\rightarrow$ idle mode (zero heavy inference).
  - Heavy models are lazily loaded on first transition and released/suspended during idle.

---

### 4. Project Directory & Module Structure

```text
iqoo-wellness/
├── build.gradle.kts                          // Root build script
├── settings.gradle.kts                       // Module registry (:app, :wellness-engine)
├── gradle.properties                         // JVM & AndroidX optimization flags
├── gradlew, gradlew.bat                      // Gradle wrapper executables
├── README.md                                 // Product pitch & OEM architecture documentation
│
├── docs/
│   ├── IMPLEMENTATION_PLAN.md                // This document
│   ├── ARCHITECTURE.md                       // High-level system & data flow diagrams
│   ├── OEM_INTEGRATION.md                    // OriginOS/AOSP system integration guide
│   ├── ANDROID_OS_CONCEPTS.md                // SystemServer, Binder, AIDL, SELinux analysis
│   └── TEST_PLAN.md                          // Deterministic testing strategy
│
├── data/
│   └── nutrition/
│       └── nutrition_seed_database.json      // Offline food & macronutrient seed dataset
│
├── future-oem/
│   ├── aidl/
│   │   └── com/iqoo/wellness/
│   │       └── IWellnessManager.aidl         // AIDL IPC contract for Native Camera
│   ├── service/
│   │   └── WellnessManagerService.kt         // Mock AOSP SystemService implementation
│   └── OEM_INTEGRATION_NOTES.md              // SELinux policy, platform permissions, HAL flow
│
├── wellness-engine/                          // Core pure Kotlin/Android library (Decoupled)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/iqoo/wellness/engine/
│       │       ├── WellnessEngine.kt         // Public engine interface
│       │       ├── WellnessEngineImpl.kt     // Pipeline coordinator
│       │       ├── scene/                    // Cascaded scene classification & routing
│       │       ├── food/                     // Food recognition & nutrition computation
│       │       ├── posture/                  // Pose estimation, joint angles & 5 exercise FSMs
│       │       ├── activity/                 // Step sensor tracker & 21-day retention worker
│       │       ├── personalization/          // Local structured retrieval and personalization
│       │       ├── storage/                  // Room entities, DAOs, WellnessDatabase
│       │       └── inference/                // On-device AI model executor abstraction
│       └── test/                             // Deterministic unit tests (math, FSMs, DB, RAG)
│
└── app/                                      // Camera-first UI application
    ├── build.gradle.kts
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── java/com/iqoo/wellness/app/
            │   ├── MainActivity.kt           // Camera Viewport & Mode Controller
            │   ├── camera/
            │   │   ├── CameraManager.kt      // CameraX lifecycle & analysis stream
            │   │   └── FrameAnalyzer.kt      // Backpressure-managed frame dispatcher
            │   └── ui/
            │       ├── CameraOverlayView.kt  // Skeletons, angle arcs, food bounding boxes HUD
            │       └── FoodCardView.kt       // Personalized portion card overlay
            └── res/                          // Layouts, drawables, strings
```

---

### 5. Implementation Phases (12-Phase Vertical Progression)

- **Phase 1: Buildable Android Project Foundation**
  - Configure root `build.gradle.kts`, `settings.gradle.kts`, and `gradle.properties` targeting Android 15 (SDK 35), minSdk 26.
  - Setup `:app` and `:wellness-engine` modules with dependencies.
  - Verify clean Gradle sync and compile.

- **Phase 2: CameraX Preview & ImageAnalysis Pipeline**
  - Implement `CameraManager` handling camera lifecycle, permissions, and `PreviewView`.
  - Implement `FrameAnalyzer` with `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` to avoid lag and memory bloat.
  - Frame delivery bridge to pass `ImageProxy` frames safely into `WellnessEngine`.

- **Phase 3: WellnessEngine API & Boundary Interfaces**
  - Define clean `WellnessEngine` interface: `analyzeScene()`, `analyzeFood()`, `analyzePose()`, `getActivitySummary()`.
  - Ensure zero leak of UI or CameraX classes into `wellness-engine`.

- **Phase 4: Food Recognition & Offline Nutrition Database**
  - Implement offline nutrition database seeded with rich food entries (Indian & international cuisines).
  - Implement food classification and portion calculation logic.

- **Phase 5: Food Personalization & Structured RAG (Hero Feature)**
  - Implement Room entities (`FoodHistoryEntity`, `PortionHistoryEntity`, `FoodPreparationContextEntity`) and DAOs.
  - Implement local structured retrieval system capable of storing and retrieving, when available:
    - Food / dish identifier
    - Typical portion size & user-confirmed quantity
    - Ingredient quantities (e.g. rice vs protein split)
    - Cooking / preparation method (e.g. pressure-cooked vs baked vs deep-fried)
    - Oil / fat usage & frying intensity
    - Ingredient substitutions (e.g. olive oil vs butter, brown rice vs white rice)
    - User corrections & confirmed adjustments
    - Frequency / recurrence of consumption
    - Previous nutrition estimates
  - Nutrition Calculation Engine computes personalized macros (calories, protein, carbs, fat) from retrieved context:
    $$\text{Fixed Food Recognition Model} \rightarrow \text{Local User History Retrieval} \rightarrow \text{Personalized Context} \rightarrow \text{Nutrition Calculation Engine} \rightarrow \text{Personalized Nutrition Estimate}$$
  - The base vision model remains strictly fixed/unchanged; local structured history continuously improves personalized context without model retraining, vector databases, or embeddings.

- **Phase 6: Posture Detection & Five Exercise State Machines**
  - Implement geometric joint angle calculations (Vector dot products with cosine rule).
  - Implement 5 distinct finite-state machines: Squat, Push-up, Bicep Curl, Lunge, Shoulder Press.
  - Generate deterministic, explainable form feedback and repetition counter.

- **Phase 7: Walking Activity & 21-Day Auto-Purge Retention**
  - Implement `StepSensorTracker` registering hardware `Sensor.TYPE_STEP_COUNTER`.
  - Implement active calorie burn calculations.
  - Implement 21-day auto-purge policy in SQLite/Room.

- **Phase 8: Cascaded Scene Inference Coordinator**
  - Implement lightweight scene pre-classifier (Food vs Exercise vs Normal scene).
  - Route frames dynamically, keeping inactive heavy pipelines idle to preserve thermal headroom.

- **Phase 9: Camera-First Polished UI (HUD Overlays)**
  - Implement `CameraOverlayView` rendering high-tech bounding boxes, skeleton wireframes, angle callouts, and repetition counters.
  - Implement floating personalized food card for immediate portion confirmation.

- **Phase 10: OEM Integration Abstraction (`future-oem/`)**
  - Implement `IWellnessManager.aidl` defining IPC transactions.
  - Implement `WellnessManagerService.kt` modeling AOSP system service lifecycle.
  - Detail OriginOS camera integration guide in `OEM_INTEGRATION_NOTES.md`.

- **Phase 11: Performance & Thermal Hardening**
  - Ensure zero frame memory leaks, bitmap recycling, and frame-rate caps (5–10 FPS for AI analysis).
  - Add graceful error fallbacks for unavailable cameras or missing sensors.

- **Phase 12: Automated Verification & Documentation**
  - Unit test suite verifying: joint angle calculations, 5 exercise state transitions, portion retrieval, 21-day DB retention purge, and scene routing.
  - Final documentation update and build validation.

---

### 6. Realistic Capabilities vs OEM Requirements

| Feature | Achievable in Prototype (No OEM Privileges) | Requires iQOO / OriginOS OEM Access |
| :--- | :--- | :--- |
| **Camera Viewfinder** | CameraX preview inside standalone APK | Native OriginOS Camera app (`com.android.camera`) |
| **AI Inference** | On-device execution via CPU/NNAPI | Qualcomm NPU / Hexagon DSP direct OEM driver access |
| **Food Recognition** | Local vision classification on camera stream | Native camera "Smart Mode" / "AI Scene Detection" toggle |
| **Personalization** | Local SQLite/Room structured history retrieval | OriginOS User Center / Vivo Account local cross-device sync |
| **Posture Analysis** | Real-time skeleton overlay on camera view | OriginOS camera sports/fitness mode |
| **Step Tracking** | Android standard `Sensor.TYPE_STEP_COUNTER` | Low-power sensor hub (CHRE) always-on coprocessor |
| **System Service** | Standalone Android Service / In-memory Singleton | `system_server` registration via `ServiceManager.addService()` |
| **IPC Boundary** | AIDL interface ready for compilation | OriginOS framework JAR and OEM platform signing key |
| **SELinux Context** | Standard third-party app domain (`untrusted_app`) | Custom `wellness_service.te` in system SEPolicy |

---

### 7. Remaining Technical Risks & Mitigations

1. **Risk:** Frame processing latency causing UI stutters on lower-end devices.  
   *Mitigation:* Strict backpressure (`KEEP_ONLY_LATEST`), asynchronous processing off the main thread (`Dispatchers.Default`), and 10 FPS analysis cap.
2. **Risk:** Hardware step sensor absent in emulators or development machines.  
   *Mitigation:* Sensor abstraction layer with auto-detecting real sensor listener and deterministic simulated test provider.
3. **Risk:** Memory bloat from accumulating food and activity records.  
   *Mitigation:* Strict Room indexing and automatic 21-day rolling purge worker.
