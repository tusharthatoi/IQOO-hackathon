package com.iqoo.wellness.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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
                latestFeedback = feedback
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
            showResult()
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
            "${feedback.repCount} reps • ${(feedback.confidence * 100).toInt()}% form"
        } else {
            "No completed frames"
        }
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
        wellnessManager.activeExerciseType = exercise
        wellnessManager.setMode(SceneType.EXERCISE)
        binding.cameraOverlay.setMode(SceneType.EXERCISE)
        
        setupCamera()
    }

    private fun updatePostureCard(feedback: PostureFeedback?) {
        if (feedback == null || feedback.poseStatus == PoseStatus.NO_PERSON || feedback.poseStatus == PoseStatus.INSUFFICIENT) {
            binding.layoutFormStatus.visibility = View.VISIBLE
            binding.txtExerciseName.text = wellnessManager.activeExerciseType.displayName
            binding.txtFormStatus.text = if (feedback?.poseStatus == PoseStatus.NO_PERSON) "Pose not detected" else "Pose insufficient"
            binding.txtFormStatus.setTextColor(Color.WHITE)
            binding.txtConfidence.text = "--"
            binding.txtReps.text = "0 reps"
            binding.txtFeedback.text = feedback?.feedbackMessage ?: "Move into camera view"
            return
        }

        binding.layoutFormStatus.visibility = View.VISIBLE
        binding.txtFormStatus.text = if (feedback.isFormCorrect) "✓ Correct Form" else "! Needs Correction"
        binding.txtFormStatus.setTextColor(if (feedback.isFormCorrect) Color.GREEN else Color.RED)
        binding.txtReps.text = "${feedback.repCount} reps"
        binding.txtFeedback.text = feedback.feedbackMessage
        binding.txtConfidence.text = "${(feedback.exerciseConfidence * 100).toInt()}%"
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