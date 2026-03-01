package com.example.universal.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.universal.R
import com.example.universal.edge.data.entity.AIDiaryEntry

class DiaryAdapter : RecyclerView.Adapter<DiaryAdapter.DiaryViewHolder>() {

    private val entries = mutableListOf<AIDiaryEntry>()

    fun submitList(newEntries: List<AIDiaryEntry>) {
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diary_entry, parent, false)
        return DiaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount(): Int = entries.size

    class DiaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.diaryDate)
        private val emotionText: TextView = itemView.findViewById(R.id.diaryEmotion)
        private val diaryText: TextView = itemView.findViewById(R.id.diaryText)

        fun bind(entry: AIDiaryEntry) {
            dateText.text = entry.date
            emotionText.text = "${entry.topEmotionType} (${String.format("%.0f%%", entry.topEmotionSuccessRate * 100)})"
            diaryText.text = entry.diaryText.ifBlank {
                "${entry.totalInteractions}回のやりとり"
            }
        }
    }
}
