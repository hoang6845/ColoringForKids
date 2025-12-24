package com.example.coloringbook

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.coloringbook.databinding.ItemColorBinding

class ColorAdapter(
    private val onColorClick: (Int) -> Unit
) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {
    private var colorPalette: List<Int>? = null
    fun updateColorPalette(list: List<Int>){
        colorPalette = list
        notifyDataSetChanged()
    }
    fun getItemAt(position: Int): Int{
        return colorPalette?.get(position)?:0
    }
    inner class ColorViewHolder(val binding: ItemColorBinding): RecyclerView.ViewHolder(binding.root){
        fun bind(color: Int) {
            binding.color.setBackgroundColor(color)
            binding.item.setOnClickListener {
                onColorClick(color)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ColorViewHolder{
        val binding = ItemColorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ColorViewHolder,
        position: Int
    ) {
        colorPalette?.get(position)?.let {
            holder.bind(it)
        }
    }

    override fun getItemCount(): Int {
        return colorPalette?.size?:0
    }
}