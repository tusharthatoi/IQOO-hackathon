# Local Webcam 8-Exercise & Posture Test Tool

## 1. Overview & Purpose
This tool is a **strictly local, isolated webcam testing script** designed to verify real-time pose detection, exercise classification, and posture scoring across all **8 supported exercises**:

### Supported Exercises
- **Original 5-Exercise Model (`original_5_exercises.onnx`):**
  1. `Jumping Jacks`
  2. `Pull ups`
  3. `Push Ups`
  4. `Russian twists`
  5. `Squats`
- **WLU 3-Exercise & Posture Model (`wlu_exercise.onnx` & `wlu_posture.onnx`):**
  6. `Arm Raise` (with Form Quality: `Correct` / `Incorrect`)
  7. `Knee Extension` (with Form Quality: `Correct` / `Incorrect`)
  8. `Sit To Stand` (with Form Quality: `Correct` / `Incorrect`)

---

## 2. Isolation & Git Status
- **Local Test Tool Only:** This tool is completely decoupled from the production Android/Kotlin application code.
- **No Duplicated Models:** It dynamically references the verified ONNX models and scalers located under `app/src/main/assets/`. No large model files are copied or duplicated.
- **No Retraining:** Existing pretrained models and scaler transformations are reused as-is without modification.
- **Ignored by Git:** The entire `tools/webcam_test/` directory, `.venv/`, and `__pycache__/` are added to `.gitignore` so they are never committed to version control.

---

## 3. Installation & Setup (Windows)

From the project root directory (`d:\iqoo project\IQOO-hackathon`):

### Step 1: Create a virtual environment
```powershell
python -m venv .venv
```

### Step 2: Activate the environment
```powershell
.venv\Scripts\activate
```

### Step 3: Install dependencies
```powershell
pip install -r tools/webcam_test/requirements.txt
```

---

## 4. Running the Webcam Test

Ensure your laptop webcam is connected and unblocked, then run:

```powershell
python tools/webcam_test/webcam_test.py
```

### Keyboard Controls
- **`Q`**: Quit the test and close the camera window.
- **`R`**: Reset the temporal smoothing prediction history buffer.

---

## 5. What the On-Screen Display (HUD) Means

The live camera window will display a mirrored preview with pose skeleton landmarks and a Heads-Up Display (HUD) card:

| Field | Meaning |
| :--- | :--- |
| **`Exercise`** | The predicted exercise movement from the unified 8-exercise decision engine. |
| **`Source`** | Indicates which model made the prediction (`Original` for 5-exercise model, `WLU` for 3-exercise model). |
| **`Confidence`** | Calibrated probability score ($0.0\% - 100.0\%$) for the active exercise prediction. |
| **`Posture`** | Form execution quality (`Correct` or `Incorrect` for WLU exercises, `N/A` for Original 5 exercises). |
| **`FPS`** | Real-time frame processing rate (typically $25 - 30$ FPS on laptop hardware). |

---

## 6. Architecture & Pipeline Summary

```
Laptop Webcam Feed
       │
       ▼
MediaPipe Pose (33 3D Landmarks)
       │
       ├──────────────────────────────────────────┐
       ▼                                          ▼
10 Geometric Joint & Ground Angles         132 Raw Pose Features
(Shoulder, Elbow, Hip, Knee, Ankle)      (33 landmarks × [x,y,z,vis])
       │                                          │
       ▼                                          ▼
Original Scaler (`original_scaler.pkl`)    WLU Scalers (`wlu_*_scaler.pkl`)
       │                                          │
       ▼                                          ▼
`original_5_exercises.onnx`                `wlu_exercise.onnx` + `wlu_posture.onnx`
       │                                          │
       └────────────────────┬─────────────────────┘
                            ▼
                Unified Decision Engine
              (Confidence Comparison &
              Temporal History Smoothing)
                            ▼
                 Live HUD Visual Overlay
```
