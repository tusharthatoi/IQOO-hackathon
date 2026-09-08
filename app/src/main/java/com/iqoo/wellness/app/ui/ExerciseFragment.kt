package com.iqoo.wellness.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.GridLayoutManager
import com.iqoo.wellness.app.R
import com.iqoo.wellness.app.WellnessManager
import com.iqoo.wellness.app.MainActivity
import com.iqoo.wellness.app.camera.CameraManager
import com.iqoo.wellness.app.databinding.FragmentExerciseBinding
import com.iqoo.wellness.engine.posture.ExerciseType
import com.iqoo.wellness.engine.posture.PoseStatus
import com.iqoo.wellness.engine.posture.PostureFeedback
import com.iqoo.wellness.engine.scene.SceneType

class ExerciseFragment : Fragment() {

    private var _binding: FragmentExerciseBinding? = null
    private val binding get() = _binding!!

    private lateinit var wellnessManager: WellnessManager
    private var cameraManager: CameraManager? = null
    private var latestFeedback: PostureFeedback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExerciseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wellnessManager = (requireActivity() as MainActivity).wellnessManager
        
        setupGrid()
        setupUI()
        wellnessManager.activeWorkout?.let { workout ->
            binding.layoutExerciseGrid.visibility = View.GONE
            binding.txtExerciseName.text = workout.exerciseType.displayName
            wellnessManager.activeExerciseType = workout.exerciseType
            wellnessManager.setMode(SceneType.EXERCISE)
            binding.cameraOverlay.setMode(SceneType.EXERCISE)
            setupCamera()
            workout.latestFeedback?.let { updatePostureCard(it) }
        }
    }

    private fun setupGrid() {
        val exercises = listOf(
            ExerciseType.JUMPING_JACKS,
            ExerciseType.PULL_UPS,
            ExerciseType.PUSH_UPS,
            ExerciseType.RUSSIAN_TWISTS,
            ExerciseType.SQUAT,
            ExerciseType.ARM_RAISE,
            ExerciseType.KNEE_EXTENSION,
            ExerciseType.SIT_TO_STAND
        )
        
        binding.rvExercises.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvExercises.adapter = ExerciseAdapter(exercises) { exercise ->
            startWorkout(exercise)
        }
    }

    private fun setupUI() {
        updateModeButtons(SceneType.EXERCISE)
        binding.btnAuto.setOnClickListener {
            wellnessManager.setMode(SceneType.NORMAL)
            binding.cameraOverlay.setMode(SceneType.NORMAL)
            updateModeButtons(SceneType.NORMAL)
        }
        binding.btnFood.setOnClickListener {
            cameraManager?.shutdown()
            (requireActivity() as MainActivity).openTab(R.id.nav_food)
        }
        binding.btnPosture.setOnClickListener {
            wellnessManager.setMode(SceneType.EXERCISE)
            binding.cameraOverlay.setMode(SceneType.EXERCISE)
            updateModeButtons(SceneType.EXERCISE)
        }

        wellnessManager.onPostureAnalyzed = { feedback ->
            activity?.runOnUiThread {
                if (feedback?.poseStatus == PoseStatus.VALID) {
                    latestFeedback = feedback
                }
                binding.cameraOverlay.updatePostureFeedback(feedback)
                android.util.Log.d(
                    "UI_STATE",
                    "exercise=${feedback?.detectedActivity} confidence=${feedback?.exerciseConfidence} " +
                        "posture=${feedback?.confidence} reps=${feedback?.repCount}"
                )
                    android.util.Log.d(
                        "EXERCISE_CONFIDENCE_TRACE",
                        "detector=${feedback?.exerciseConfidence} engine=${feedback?.exerciseConfidence} " +
                        "manager=${feedback?.exerciseConfidence} ui=${feedback?.exerciseConfidence} " +
                        "selectedExercise=${wellnessManager.activeExerciseType.displayName} " +
                        "detectedExercise=${feedback?.detectedActivity} confidence=${feedback?.exerciseConfidence}"
                    )
                updatePostureCard(feedback)
            }
        }

        binding.btnFinishWorkout.setOnClickListener {
            saveWorkout()
        }

        binding.btnCancelWorkout.setOnClickListener {
            showCancelConfirmation()
        }

        binding.btnResultDone.setOnClickListener {
            resetToGrid()
        }

        binding.btnResultAgain.setOnClickListener {
            val exercise = wellnessManager.activeExerciseType
            resetToGrid()
            startWorkout(exercise)
        }
    }

    private fun showResult() {
        cameraManager?.shutdown()
        wellnessManager.setMode(SceneType.NORMAL)
        binding.layoutWorkoutResult.visibility = View.VISIBLE
        val feedback = latestFeedback
        binding.txtResultStats.text = if (feedback != null) {
            val postureText = if (feedback.poseStatus == PoseStatus.VALID && feedback.confidence > 0f && feedback.confidence.isFinite()) {
                "${(feedback.confidence * 100).toInt()}% form"
            } else {
                "posture confidence unavailable"
            }
            "${feedback.repCount} reps • $postureText"
        } else {
            "No completed frames"
        }
    }

    private fun saveWorkout() {
        latestFeedback = wellnessManager.activeWorkout?.latestFeedback ?: latestFeedback
        binding.btnFinishWorkout.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            wellnessManager.saveActiveWorkout()
            activity?.runOnUiThread {
                binding.btnFinishWorkout.isEnabled = true
                showResult()
            }
        }
    }

    private fun showCancelConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Cancel workout?")
            .setMessage("Your current workout will be discarded.")
            .setNegativeButton("Keep Working", null)
            .setPositiveButton("Cancel Workout") { _, _ ->
                wellnessManager.cancelWorkout()
                resetToGrid()
            }
            .show()
    }

    private fun resetToGrid() {
        binding.layoutWorkoutResult.visibility = View.GONE
        binding.layoutExerciseGrid.visibility = View.VISIBLE
        cameraManager?.shutdown()
        wellnessManager.setMode(SceneType.NORMAL)
        latestFeedback = null
    }

    private fun startWorkout(exercise: ExerciseType) {
        binding.layoutExerciseGrid.visibility = View.GONE
        binding.txtExerciseName.text = exercise.displayName
        wellnessManager.startWorkout(exercise)
        binding.cameraOverlay.setMode(SceneType.EXERCISE)
        
        setupCamera()
    }

    private fun updatePostureCard(feedback: PostureFeedback?) {
        if (feedback == null || feedback.poseStatus == PoseStatus.NO_PERSON || feedback.poseStatus == PoseStatus.INSUFFICIENT) {
            binding.layoutFormStatus.visibility = View.VISIBLE
            binding.txtExerciseName.text = wellnessManager.activeExerciseType.displayName
            binding.txtFormStatus.text = "Waiting for person…"
            binding.txtFormStatus.setTextColor(Color.WHITE)
            binding.txtConfidence.text = "—"
            val reps = wellnessManager.activeWorkout?.latestFeedback?.repCount ?: 0
            binding.txtReps.text = "$reps reps"
            binding.txtFeedback.text = "Move into camera view"
            return
        }

        // Raw ONNX values — never modified or fabricated
        val rawExerciseConfidence = feedback.exerciseConfidence
        val repCount = feedback.repCount

        // Display real confidence; "—" if ONNX returned 0 (confidence not yet resolved)
        val displayConfidenceText = if (rawExerciseConfidence > 0f)
            "${(rawExerciseConfidence * 100).toInt()}%"
        else
            "—"

        android.util.Log.i(
            "EXERCISE_CONFIDENCE",
            "selectedExercise=${wellnessManager.activeExerciseType.displayName} " +
                "detectedExercise=${feedback.detectedActivity} " +
                "rawOnnxConfidence=$rawExerciseConfidence " +
                "repCount=$repCount " +
                "uiDisplay=$displayConfidenceText"
        )

        binding.layoutFormStatus.visibility = View.VISIBLE
        val postureAvailable = feedback.confidence > 0f && feedback.confidence.isFinite()
        binding.txtFormStatus.text = if (postureAvailable) {
            if (feedback.isFormCorrect) "✓ Correct Form" else "! Needs Correction"
        } else {
            "Form: —"
        }
        binding.txtFormStatus.setTextColor(
            if (!postureAvailable) Color.WHITE else if (feedback.isFormCorrect) Color.GREEN else Color.RED
        )
        binding.txtReps.text = "$repCount reps"
        binding.txtFeedback.text = feedback.feedbackMessage
        binding.txtConfidence.text = displayConfidenceText
    }

    private fun setupCamera() {
        cameraManager = CameraManager(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            previewView = binding.previewView,
            frameListener = wellnessManager
        )
        cameraManager?.startCamera()
    }

    private fun updateModeButtons(mode: SceneType) {
        binding.btnAuto.setBackgroundResource(if (mode == SceneType.NORMAL) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        binding.btnFood.setBackgroundResource(if (mode == SceneType.FOOD) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        binding.btnPosture.setBackgroundResource(if (mode == SceneType.EXERCISE) R.drawable.bg_mode_pill_active else R.drawable.bg_mode_pill_inactive)
        binding.btnAuto.setTextColor(if (mode == SceneType.NORMAL) Color.WHITE else Color.parseColor("#CFD8DC"))
        binding.btnFood.setTextColor(if (mode == SceneType.FOOD) Color.WHITE else Color.parseColor("#CFD8DC"))
        binding.btnPosture.setTextColor(if (mode == SceneType.EXERCISE) Color.WHITE else Color.parseColor("#CFD8DC"))
    }

    override fun onDestroyView() {
        wellnessManager.clearCallbacks()
        super.onDestroyView()
        cameraManager?.shutdown()
        _binding = null
    }
}