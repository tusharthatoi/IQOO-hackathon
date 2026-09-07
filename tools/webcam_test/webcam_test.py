"""
================================================================================
LOCAL WEBCAM 8-EXERCISE & POSTURE TEST TOOL
================================================================================
Isolated local testing tool for the unified 8-exercise detection system:
  - Original 5 Exercises: Jumping Jacks, Pull ups, Push Ups, Russian twists, Squats
  - WLU 3 Exercises: Arm Raise, Knee Extension, Sit To Stand
  - WLU Posture Quality: Correct / Incorrect

Reuses existing pretrained ONNX models and preprocessing assets directly from:
  app/src/main/assets/

This tool is strictly local and isolated from production Android code.
"""

import sys
import os
import time
import math
from pathlib import Path
from collections import deque
import warnings

# Suppress sklearn/numpy version warnings for clean terminal output
warnings.filterwarnings("ignore")

# Force UTF-8 and unbuffered output so Windows console never crashes on Unicode
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", line_buffering=True, errors="replace")
    except Exception:
        pass

import cv2
import numpy as np
import joblib
import onnxruntime as ort
import mediapipe as mp


WINDOW_NAME = "WEBCAM 8-EXERCISE TEST"


# ==============================================================================
# 1. ASSET DISCOVERY & PATH RESOLUTION
# ==============================================================================

def find_assets_dir() -> Path:
    """Locates app/src/main/assets dynamically from script location."""
    current = Path(__file__).resolve()
    for parent in [current.parents[2], current.parents[1], current.parent]:
        candidate = parent / "app" / "src" / "main" / "assets"
        if candidate.exists() and (candidate / "original_5_exercises.onnx").exists():
            return candidate

    cwd_candidate = Path.cwd() / "app" / "src" / "main" / "assets"
    if cwd_candidate.exists() and (cwd_candidate / "original_5_exercises.onnx").exists():
        return cwd_candidate

    raise FileNotFoundError(
        "Could not locate app/src/main/assets. "
        "Please run this script from the project root directory."
    )


# ==============================================================================
# 2. FEATURE EXTRACTION HELPERS
# ==============================================================================

def calculate_angle_3pt(a: np.ndarray, b: np.ndarray, c: np.ndarray) -> float:
    """Calculates interior 2D angle in degrees [0, 180] at vertex B."""
    ba = a - b
    bc = c - b
    norm_ba = np.linalg.norm(ba)
    norm_bc = np.linalg.norm(bc)
    if norm_ba == 0.0 or norm_bc == 0.0:
        return 180.0
    cosine = np.dot(ba, bc) / (norm_ba * norm_bc)
    cosine = np.clip(cosine, -1.0, 1.0)
    return float(np.degrees(np.arccos(cosine)))


def calculate_ground_angle_2pt(p1: np.ndarray, p2: np.ndarray) -> float:
    """Calculates angle of vector p1->p2 relative to the horizontal ground plane."""
    dx = p2[0] - p1[0]
    dy = p2[1] - p1[1]
    rad = math.atan2(abs(dy), abs(dx))
    return float(math.degrees(rad))


def extract_original_10_features(landmarks) -> np.ndarray:
    """
    Extracts the exact 10 angle features for the original 5-exercise model:
      1. Shoulder_Angle
      2. Elbow_Angle
      3. Hip_Angle
      4. Knee_Angle
      5. Ankle_Angle
      6. Shoulder_Ground_Angle
      7. Elbow_Ground_Angle
      8. Hip_Ground_Angle
      9. Knee_Ground_Angle
      10. Ankle_Ground_Angle
    """
    # Left side landmarks
    l_sh = np.array([landmarks[11].x, landmarks[11].y])
    l_el = np.array([landmarks[13].x, landmarks[13].y])
    l_wr = np.array([landmarks[15].x, landmarks[15].y])
    l_hp = np.array([landmarks[23].x, landmarks[23].y])
    l_kn = np.array([landmarks[25].x, landmarks[25].y])
    l_an = np.array([landmarks[27].x, landmarks[27].y])
    l_ft = np.array([landmarks[31].x, landmarks[31].y])
    l_vis = (landmarks[11].visibility + landmarks[13].visibility +
             landmarks[23].visibility + landmarks[25].visibility) / 4.0

    # Right side landmarks
    r_sh = np.array([landmarks[12].x, landmarks[12].y])
    r_el = np.array([landmarks[14].x, landmarks[14].y])
    r_wr = np.array([landmarks[16].x, landmarks[16].y])
    r_hp = np.array([landmarks[24].x, landmarks[24].y])
    r_kn = np.array([landmarks[26].x, landmarks[26].y])
    r_an = np.array([landmarks[28].x, landmarks[28].y])
    r_ft = np.array([landmarks[32].x, landmarks[32].y])
    r_vis = (landmarks[12].visibility + landmarks[14].visibility +
             landmarks[24].visibility + landmarks[26].visibility) / 4.0

    def compute_side_angles(sh, el, wr, hp, kn, an, ft):
        sh_ang = calculate_angle_3pt(el, sh, hp)
        el_ang = calculate_angle_3pt(sh, el, wr)
        hp_ang = calculate_angle_3pt(sh, hp, kn)
        kn_ang = calculate_angle_3pt(hp, kn, an)
        an_ang = calculate_angle_3pt(kn, an, ft)

        sh_grd = calculate_ground_angle_2pt(sh, el)
        el_grd = calculate_ground_angle_2pt(el, wr)
        hp_grd = calculate_ground_angle_2pt(hp, kn)
        kn_grd = calculate_ground_angle_2pt(kn, an)
        an_grd = calculate_ground_angle_2pt(an, ft)

        return np.array([
            sh_ang, el_ang, hp_ang, kn_ang, an_ang,
            sh_grd, el_grd, hp_grd, kn_grd, an_grd
        ], dtype=np.float32)

    l_angles = compute_side_angles(l_sh, l_el, l_wr, l_hp, l_kn, l_an, l_ft)
    r_angles = compute_side_angles(r_sh, r_el, r_wr, r_hp, r_kn, r_an, r_ft)

    # Use dominant side or blend based on visibility
    if r_vis > l_vis + 0.1:
        return r_angles
    elif l_vis > r_vis + 0.1:
        return l_angles
    else:
        return (l_angles + r_angles) / 2.0


def extract_wlu_132_features(landmarks) -> np.ndarray:
    """
    Extracts the exact 132-feature vector for WLU models:
    33 landmarks × [x, y, z, visibility] flattened in sequence (0..32).
    """
    features = []
    for lm in landmarks:
        features.extend([lm.x, lm.y, lm.z, lm.visibility])
    return np.array(features, dtype=np.float32)


# ==============================================================================
# 3. PIPELINE WRAPPER CLASS
# ==============================================================================

class UnifiedExercisePipeline:
    def __init__(self, assets_dir: Path):
        self.assets_dir = assets_dir

        # 1. Load ONNX Models
        orig_model_path = str(assets_dir / "original_5_exercises.onnx")
        wlu_ex_path = str(assets_dir / "wlu_exercise.onnx")
        wlu_post_path = str(assets_dir / "wlu_posture.onnx")

        self.orig_session = ort.InferenceSession(orig_model_path)
        self.wlu_ex_session = ort.InferenceSession(wlu_ex_path)
        self.wlu_post_session = ort.InferenceSession(wlu_post_path)

        # 2. Load Preprocessing Files
        self.orig_scaler = joblib.load(str(assets_dir / "original_scaler.pkl"))
        self.orig_le = joblib.load(str(assets_dir / "original_label_encoder.pkl"))

        self.wlu_ex_scaler = joblib.load(str(assets_dir / "wlu_exercise_scaler.pkl"))
        self.wlu_ex_labels = joblib.load(str(assets_dir / "wlu_exercise_labels.pkl"))

        self.wlu_post_scaler = joblib.load(str(assets_dir / "wlu_posture_scaler.pkl"))

        # Posture label map: 0 = Incorrect, 1 = Correct (per metadata.json)
        self.posture_labels = {0: "Incorrect", 1: "Correct"}

        # 3. Verify ONNX input/output specs
        self.orig_input_name = self.orig_session.get_inputs()[0].name
        self.wlu_ex_input_name = self.wlu_ex_session.get_inputs()[0].name
        self.wlu_post_input_name = self.wlu_post_session.get_inputs()[0].name

        # Temporal smoothing deque
        self.history_size = 15
        self.prediction_history = deque(maxlen=self.history_size)

    def reset_history(self):
        """Clears the temporal smoothing buffer."""
        self.prediction_history.clear()

    def process_landmarks(self, landmarks):
        """
        Runs both model families and determines the unified 8-exercise prediction.
        """
        # --- A. Original 5-Exercise Model ---
        feat_10 = extract_original_10_features(landmarks).reshape(1, -1)
        feat_10_scaled = self.orig_scaler.transform(feat_10).astype(np.float32)
        orig_res = self.orig_session.run(None, {self.orig_input_name: feat_10_scaled})

        orig_class_idx = int(orig_res[0][0])
        orig_class_name = self.orig_le.classes_[orig_class_idx]
        orig_prob_map = orig_res[1][0]
        orig_conf = float(orig_prob_map.get(orig_class_idx, 0.5))

        # --- B. WLU 3-Exercise Model ---
        feat_132 = extract_wlu_132_features(landmarks).reshape(1, -1)
        feat_132_ex_scaled = self.wlu_ex_scaler.transform(feat_132).astype(np.float32)
        wlu_ex_res = self.wlu_ex_session.run(None, {self.wlu_ex_input_name: feat_132_ex_scaled})

        wlu_ex_class_name = str(wlu_ex_res[0][0])
        wlu_ex_prob_map = wlu_ex_res[1][0]
        wlu_ex_conf = float(wlu_ex_prob_map.get(wlu_ex_class_name, 0.5))

        # --- C. WLU Posture Model ---
        feat_132_post_scaled = self.wlu_post_scaler.transform(feat_132).astype(np.float32)
        wlu_post_res = self.wlu_post_session.run(None, {self.wlu_post_input_name: feat_132_post_scaled})

        posture_idx = int(wlu_post_res[0][0])
        posture_name = self.posture_labels.get(posture_idx, "Unknown")
        posture_prob_map = wlu_post_res[1][0]
        posture_conf = float(posture_prob_map.get(posture_idx, 0.5))

        # --- D. Unified Decision ---
        if orig_conf >= wlu_ex_conf:
            selected_exercise = orig_class_name
            selected_source = "Original"
            selected_conf = orig_conf
            selected_posture = "N/A"
            selected_posture_conf = None
        else:
            selected_exercise = wlu_ex_class_name
            selected_source = "WLU"
            selected_conf = wlu_ex_conf
            selected_posture = posture_name
            selected_posture_conf = posture_conf

        # --- E. Temporal Smoothing ---
        self.prediction_history.append((
            selected_exercise,
            selected_source,
            selected_conf,
            selected_posture,
            selected_posture_conf
        ))

        # Confidence-weighted majority voting
        vote_weights = {}
        source_map = {}
        conf_accumulator = {}
        posture_map = {}

        for ex, src, conf, post, post_c in self.prediction_history:
            vote_weights[ex] = vote_weights.get(ex, 0.0) + conf
            source_map[ex] = src
            conf_accumulator[ex] = conf_accumulator.get(ex, []) + [conf]
            if post != "N/A":
                posture_map[ex] = posture_map.get(ex, []) + [(post, post_c)]

        smoothed_exercise = max(vote_weights, key=vote_weights.get)
        smoothed_source = source_map[smoothed_exercise]
        smoothed_conf = float(np.mean(conf_accumulator[smoothed_exercise]))

        if smoothed_source == "WLU" and smoothed_exercise in posture_map and posture_map[smoothed_exercise]:
            postures = posture_map[smoothed_exercise]
            smoothed_posture = postures[-1][0]
            smoothed_posture_conf = postures[-1][1]
        else:
            smoothed_posture = "N/A"
            smoothed_posture_conf = None

        return {
            "exercise": smoothed_exercise,
            "source": smoothed_source,
            "confidence": smoothed_conf,
            "posture": smoothed_posture,
            "posture_confidence": smoothed_posture_conf,
            "raw_orig": (orig_class_name, orig_conf),
            "raw_wlu": (wlu_ex_class_name, wlu_ex_conf)
        }


# ==============================================================================
# 4. HUD DRAWING HELPER
# ==============================================================================

def draw_hud(frame, result_dict, fps: float, pose_detected: bool):
    """Draws a clean, readable on-screen display on the mirrored video frame."""
    h, w, _ = frame.shape

    card_w = min(480, w - 40)
    card_h = 240 if (result_dict and result_dict.get("source") == "WLU") else 190
    card_x = 20
    card_y = 20

    overlay = frame.copy()
    cv2.rectangle(
        overlay,
        (card_x, card_y),
        (card_x + card_w, card_y + card_h),
        (18, 24, 36),
        -1
    )
    cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)
    cv2.rectangle(
        frame,
        (card_x, card_y),
        (card_x + card_w, card_y + card_h),
        (0, 229, 255),
        2
    )

    cv2.putText(
        frame,
        "LIVE EXERCISE DETECTION",
        (card_x + 15, card_y + 32),
        cv2.FONT_HERSHEY_DUPLEX,
        0.65,
        (0, 229, 255),
        2,
        cv2.LINE_AA
    )

    if not pose_detected:
        cv2.putText(
            frame,
            "No pose detected",
            (card_x + 15, card_y + 80),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.7,
            (0, 140, 255),
            2,
            cv2.LINE_AA
        )
        cv2.putText(
            frame,
            "Move into camera frame",
            (card_x + 15, card_y + 115),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (180, 190, 200),
            1,
            cv2.LINE_AA
        )
        cv2.putText(
            frame,
            f"FPS: {int(fps)}",
            (card_x + 15, card_y + 155),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (255, 255, 255),
            1,
            cv2.LINE_AA
        )
        return

    ex_name = result_dict["exercise"]
    src_name = result_dict["source"]
    conf_pct = result_dict["confidence"] * 100.0

    cv2.putText(
        frame,
        f"Exercise: {ex_name}",
        (card_x + 15, card_y + 70),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.75,
        (255, 255, 255),
        2,
        cv2.LINE_AA
    )

    cv2.putText(
        frame,
        f"Source: {src_name}   |   Conf: {conf_pct:.1f}%",
        (card_x + 15, card_y + 105),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.55,
        (0, 229, 255),
        1,
        cv2.LINE_AA
    )

    posture = result_dict["posture"]
    if src_name == "WLU" and posture != "N/A":
        post_conf = result_dict.get("posture_confidence")
        post_conf_str = f" ({post_conf * 100.0:.1f}%)" if post_conf else ""
        post_color = (0, 230, 118) if posture == "Correct" else (0, 140, 255)

        cv2.putText(
            frame,
            f"Posture: {posture}{post_conf_str}",
            (card_x + 15, card_y + 145),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.65,
            post_color,
            2,
            cv2.LINE_AA
        )
        cv2.putText(
            frame,
            f"FPS: {int(fps)}   |   [Q] Quit   [R] Reset",
            (card_x + 15, card_y + 185),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (160, 170, 180),
            1,
            cv2.LINE_AA
        )
    else:
        cv2.putText(
            frame,
            "Posture: N/A (Original 5 Model)",
            (card_x + 15, card_y + 140),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (180, 190, 200),
            1,
            cv2.LINE_AA
        )
        cv2.putText(
            frame,
            f"FPS: {int(fps)}   |   [Q] Quit   [R] Reset",
            (card_x + 15, card_y + 170),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (160, 170, 180),
            1,
            cv2.LINE_AA
        )


# ==============================================================================
# 5. MAIN WEBCAM EXECUTION LOOP
# ==============================================================================

def open_camera():
    """Tries opening webcam on Windows using index 0, then index 1 with DirectShow and default backends."""
    for idx in [0, 1]:
        # Try DirectShow on Windows for fastest hardware handshake
        cap = cv2.VideoCapture(idx, cv2.CAP_DSHOW)
        if cap.isOpened():
            # Test frame grab
            ret, _ = cap.read()
            if ret:
                return cap
            cap.release()

        # Fallback to default backend
        cap = cv2.VideoCapture(idx)
        if cap.isOpened():
            ret, _ = cap.read()
            if ret:
                return cap
            cap.release()

    return None


def main():
    print("\n" + "=" * 60, flush=True)
    print("WEBCAM 8-EXERCISE TEST", flush=True)
    print("=" * 60 + "\n", flush=True)

    try:
        assets_dir = find_assets_dir()
    except Exception as e:
        print(f"Error: {e}", flush=True)
        return

    try:
        pipeline = UnifiedExercisePipeline(assets_dir)
    except Exception as e:
        print(f"Error initializing pipeline: {e}", flush=True)
        return

    print("Models:\n", flush=True)
    print("✓ Original 5-exercise model", flush=True)
    print("✓ WLU exercise model", flush=True)
    print("✓ WLU posture model\n", flush=True)

    print("Exercises:\n", flush=True)
    for ex in [
        "Jumping Jacks", "Pull ups", "Push Ups", "Russian twists", "Squats",
        "Arm Raise", "Knee Extension", "Sit To Stand"
    ]:
        print(f"✓ {ex}", flush=True)

    print("\nFeature sizes:\n", flush=True)
    print("Original: 10", flush=True)
    print("WLU exercise: 132", flush=True)
    print("WLU posture: 132\n", flush=True)

    print("Opening camera...", flush=True)
    cap = open_camera()
    if cap is None:
        print("Camera:", flush=True)
        print("✗ Camera could not be opened.", flush=True)
        print("Please check that your webcam is connected, enabled, and not in use by another app.\n", flush=True)
        print("=" * 60, flush=True)
        return

    print("Camera:\n", flush=True)
    print("✓ Camera opened successfully\n", flush=True)
    print("=" * 60, flush=True)
    print("OpenCV Preview Window is starting now.", flush=True)
    print("Look for the window titled: 'WEBCAM 8-EXERCISE TEST'", flush=True)
    print("Press 'q' in the camera window to quit, 'r' to reset smoothing history.\n", flush=True)

    # 1. Create named window and resize
    cv2.namedWindow(WINDOW_NAME, cv2.WINDOW_NORMAL)
    cv2.resizeWindow(WINDOW_NAME, 1280, 720)

    # Bring window to foreground on Windows
    try:
        cv2.setWindowProperty(WINDOW_NAME, cv2.WND_PROP_TOPMOST, 1)
        cv2.setWindowProperty(WINDOW_NAME, cv2.WND_PROP_TOPMOST, 0)
    except Exception:
        pass

    mp_pose = mp.solutions.pose
    mp_drawing = mp.solutions.drawing_utils
    mp_drawing_styles = mp.solutions.drawing_styles

    pose_detector = mp_pose.Pose(
        static_image_mode=False,
        model_complexity=1,
        smooth_landmarks=True,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5
    )

    prev_time = time.time()
    fps = 30.0

    try:
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret or frame is None:
                print("Error: Camera opened but failed to receive video frames. Retrying...", flush=True)
                time.sleep(0.05)
                continue

            # Mirror preview horizontally
            frame = cv2.flip(frame, 1)

            curr_time = time.time()
            dt = curr_time - prev_time
            prev_time = curr_time
            if dt > 0:
                fps = 0.9 * fps + 0.1 * (1.0 / dt)

            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            rgb_frame.flags.writeable = False
            results = pose_detector.process(rgb_frame)
            rgb_frame.flags.writeable = True

            result_dict = None
            pose_detected = False

            if results.pose_landmarks:
                pose_detected = True
                landmarks = results.pose_landmarks.landmark

                mp_drawing.draw_landmarks(
                    frame,
                    results.pose_landmarks,
                    mp_pose.POSE_CONNECTIONS,
                    landmark_drawing_spec=mp_drawing_styles.get_default_pose_landmarks_style()
                )

                try:
                    result_dict = pipeline.process_landmarks(landmarks)
                except Exception as e:
                    print(f"Inference error on frame: {e}", flush=True)

            draw_hud(frame, result_dict, fps, pose_detected)

            # Display frame in named window
            cv2.imshow(WINDOW_NAME, frame)

            # Must be called every frame for OpenCV GUI event processing
            key = cv2.waitKey(1) & 0xFF
            if key == ord('q') or key == ord('Q'):
                print("User requested quit. Exiting...", flush=True)
                break
            elif key == ord('r') or key == ord('R'):
                pipeline.reset_history()
                print("Prediction history reset.", flush=True)

    finally:
        cap.release()
        cv2.destroyAllWindows()
        pose_detector.close()
        print("Webcam session closed successfully.", flush=True)


if __name__ == "__main__":
    main()
