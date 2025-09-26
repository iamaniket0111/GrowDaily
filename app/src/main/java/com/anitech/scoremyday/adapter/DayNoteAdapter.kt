package com.anitech.scoremyday.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.anitech.scoremyday.R
import com.anitech.scoremyday.data_class.DayNoteEntity

class DayNoteAdapter(private var notes: List<DayNoteEntity>) :
    RecyclerView.Adapter<DayNoteAdapter.ViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
       val view: View = LayoutInflater.from(parent.context).inflate(R.layout.rv_specific_day_note_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val note = notes[position]
        holder.bind(note.note)
    }

    override fun getItemCount(): Int {
       return notes.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val taskNote: TextView = itemView.findViewById(R.id.taskNote)
        fun bind(taskNote:String){
            this.taskNote.text = taskNote
        }
    }

    fun updateData(newList: List<DayNoteEntity>) {
        notes = newList
        notifyDataSetChanged()
    }
}