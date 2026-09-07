package com.iqoo.wellness.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iqoo.wellness.app.R
import com.iqoo.wellness.engine.posture.ExerciseType

class ExerciseAdapter(
    private val exercises: List<ExerciseType>,
    private val onExerciseSelected: (ExerciseType) -> Unit
) : RecyclerView.Adapter<ExerciseAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgExerciseIcon)
        val name: TextView = view.findViewById(R.id.txtExerciseName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.name.text = exercise.displayName
        // Icons can be set based on exercise type
        holder.icon.setImageResource(R.drawable.ic_exercise)
        
        holder.itemView.setOnClickListener {
            onExerciseSelected(exercise)
        }
    }

    override fun getItemCount() = exercises.size
}