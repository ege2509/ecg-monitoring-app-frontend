package com.example.ens492frontend

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ens492frontend.R
import com.example.ens492frontend.models.Warning

class WarningsAdapter : RecyclerView.Adapter<WarningsAdapter.WarningViewHolder>() {

    private var warnings = mutableListOf<Warning>()

    fun updateWarnings(newWarnings: List<Warning>) {
        warnings.clear()
        warnings.addAll(newWarnings)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WarningViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_warning, parent, false)
        return WarningViewHolder(view)
    }

    override fun onBindViewHolder(holder: WarningViewHolder, position: Int) {
        holder.bind(warnings[position])
    }

    override fun getItemCount(): Int = warnings.size

    class WarningViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val warningType: TextView = itemView.findViewById(R.id.textWarningType)
        private val warningDetails: TextView = itemView.findViewById(R.id.textWarningDetails)

        fun bind(warning: Warning) {
            warningType.text = warning.type
            warningDetails.text = warning.details
        }
    }
}