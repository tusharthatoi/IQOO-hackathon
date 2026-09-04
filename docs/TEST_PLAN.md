# Test Plan: iQOO Wellness AI Engine
## Verification & Validation Strategy

### 1. Testing Philosophy
On-device machine learning and camera pipelines frequently suffer from flaky or non-deterministic tests due to varying lighting conditions, camera hardware differences, and platform sensor availability.

Our testing strategy strictly decouples:
1. **Mathematical & Business Logic Tests (Deterministic Unit Tests):** Tested with 100% deterministic inputs without requiring physical camera sensors or GPU hardware.
2. **State Machine & Rule Validation:** Verifying all transitions and form faults across all 5 supported exercises.
3. **Storage & 21-Day Retention Verification:** Verifying database queries, portion retrieval, and rolling window deletion.
4. **Integration & Build Verification:** Clean compilation of all modules and APK generation.

---

### 2. Test Suites

#### Suite 1: Joint Angle Mathematics (`JointAngleCalculatorTest`)
- Test 90° right angle formed by (0,1), (0,0), (1,0).
- Test 180° straight collinear limb (0,1), (0,0), (0,-1).
- Test 45° acute angle.
- Test handling of zero-vector or overlapping coordinates without NaN/crash.

#### Suite 2: Exercise Finite State Machines (`ExerciseStateMachineTest`)
- **Squat FSM:**
  - Verify transition from `READY` $\rightarrow$ `DESCENT` $\rightarrow$ `INFLECTION` $\rightarrow$ `ASCENT` $\rightarrow$ `REP_COMPLETED`.
  - Verify depth violation triggers: when knees do not bend below threshold, rep is flagged incomplete.
- **Push-up FSM:**
  - Verify full elbow extension $\rightarrow$ 90° flexion $\rightarrow$ rep count increment.
- **Bicep Curl FSM:**
  - Verify arm curl lockout $\rightarrow$ peak contraction $\rightarrow$ release.
- **Lunge FSM & Shoulder Press FSM:**
  - Verify proper alternating leg tracking and overhead lockout validation.

#### Suite 3: Food Personalization & Structured RAG (`FoodPersonalizationTest`)
- Verify first-time encounter: Defaults to standard portion (e.g., 200g).
- User confirms 300g portion $\rightarrow$ stored in `PortionHistory`.
- Verify second encounter: Query returns learned typical portion of 300g instead of default 200g.
- Nutrition recalculation test: Scaled macros accurately match ratio $(300 / 100) \times \text{macros}$.

#### Suite 4: 21-Day Activity Retention Purge (`ActivityRetentionTest`)
- Insert activity records with timestamps:
  - 5 days ago
  - 15 days ago
  - 21 days ago
  - 22 days ago (out of policy)
  - 40 days ago (out of policy)
- Execute retention cleanup worker / query.
- Assert that records $> 21$ days old are permanently purged, while records $\le 21$ days old are strictly preserved.

#### Suite 5: Cascaded Scene Routing (`CascadedSceneRouterTest`)
- Verify scene classifier routing:
  - Food frame triggers Food Pipeline, keeps Pose Pipeline dormant.
  - Person frame triggers Pose Pipeline, keeps Food Pipeline dormant.
  - Empty background triggers idle state.

---

### 3. Execution Commands
```powershell
# Run deterministic unit test suite
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew testDebugUnitTest --no-daemon

# Verify complete APK assembly
.\gradlew assembleDebug --no-daemon
```
