package com.example.ens492frontend


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ens492frontend.models.EcgReading

/**
 * Adapter for displaying ECG readings in a RecyclerView
 */
class EcgReadingsAdapter(
    private val onItemClick: (EcgReading) -> Unit
) : ListAdapter<EcgReading, EcgReadingsAdapter.EcgReadingViewHolder>(EcgReadingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EcgReadingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ecg_reading, parent, false)
        return EcgReadingViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: EcgReadingViewHolder, position: Int) {
        val reading = getItem(position)
        holder.bind(reading)
    }

    class EcgReadingViewHolder(
        itemView: View,
        private val onItemClick: (EcgReading) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvHeartRate: TextView = itemView.findViewById(R.id.tvHeartRate)
        private val tvAbnormalities: TextView = itemView.findViewById(R.id.tvAbnormalities)

        fun bind(reading: EcgReading) {
            tvTimestamp.text = reading.timestamp
            tvHeartRate.text = "${reading.heartRate} BPM"

            // Format abnormalities text
            val abnormalitiesText = if (reading.abnormalities.isEmpty()) {
                "No abnormalities detected"
            } else {
                reading.abnormalities.entries
                    .filter { it.value >= EcgVisualizationView.ABNORMALITY_THRESHOLD }
                    .joinToString(", ") { "${it.key}: ${(it.value * 100).toInt()}%" }
            }
            tvAbnormalities.text = abnormalitiesText

            // Set click listener
            itemView.setOnClickListener { onItemClick(reading) }
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    class EcgReadingDiffCallback : DiffUtil.ItemCallback<EcgReading>() {
        override fun areItemsTheSame(oldItem: EcgReading, newItem: EcgReading): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: EcgReading, newItem: EcgReading): Boolean {
            return oldItem == newItem
        }
    }
}