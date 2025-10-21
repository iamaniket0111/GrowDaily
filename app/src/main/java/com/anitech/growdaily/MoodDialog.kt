package com.anitech.growdaily

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.anitech.growdaily.adapter.MoodAdapter
import com.anitech.growdaily.data_class.MoodHistoryItem
import com.anitech.growdaily.data_class.MoodItem

class MoodDialog(
    context: Context,
    private val moodHistoryItem: MoodHistoryItem,
    private val listener: OnMoodSelectedListener
) {

    interface OnMoodSelectedListener {
        fun onMoodSelected(updatedMood: MoodHistoryItem)
    }

    private val dialog = Dialog(context)
    private val moodView = LayoutInflater.from(context).inflate(R.layout.dialog_mood_track, null)
    private val recyclerView = moodView.findViewById<RecyclerView>(R.id.moodTrackerRv)

    private val moodList = listOf(
        MoodItem("😀", "Happy"),
        MoodItem("😢", "Sad"),
        MoodItem("😡", "Angry"),
        MoodItem("😴", "Sleepy"),
        MoodItem("🤩", "Excited"),
        MoodItem("😐", "Neutral"),
        MoodItem("😭", "Crying"),
        MoodItem("😍", "Love")
    )

    init {
        val moodAdapter = MoodAdapter(moodList, object : MoodAdapter.OnItemClickListener {
            override fun onMoodItemClick(moodItem: MoodItem) {
                Log.d("MoodDialog", "Selected Mood: ${moodItem.emoji}")

                val updatedItem = moodHistoryItem.copy(
                    emoji = moodItem.emoji
                )

                listener.onMoodSelected(updatedItem)
                dialog.dismiss()
            }
        })

        recyclerView.layoutManager = GridLayoutManager(context, 4)
        recyclerView.adapter = moodAdapter

        val selectedIndex = moodList.indexOfFirst { it.emoji == moodHistoryItem.emoji }
        if (selectedIndex != -1) {
            moodAdapter.setSelectedPosition(selectedIndex)
        }

        dialog.setContentView(moodView)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun show() {
        dialog.show()
    }
}
