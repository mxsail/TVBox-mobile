package com.github.tvbox.osc.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.tvbox.osc.R
import com.google.android.material.chip.Chip

/** Java 侧 SAM 友好的点击回调 */
fun interface CategoryItemClick {
    fun onClick(position: Int)
}

/**
 * 全部分类网格 chips 适配器(首页「展开全部」弹窗用), 当前分类高亮。
 */
class CategoryChipAdapter(private val onItemClick: CategoryItemClick) :
    RecyclerView.Adapter<CategoryChipAdapter.Holder>() {

    private val items = ArrayList<String>()
    private var current = -1

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val chip: Chip = view.findViewById(R.id.chip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_chip, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val name = items[position]
        holder.chip.text = name
        holder.chip.isChecked = position == current
        holder.chip.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (pos != current) {
                val old = current
                current = pos
                if (old >= 0) notifyItemChanged(old)
                notifyItemChanged(pos)
                onItemClick.onClick(pos)
            }
        }
    }

    fun setData(names: List<String>, currentPos: Int) {
        items.clear()
        items.addAll(names)
        current = currentPos
        notifyDataSetChanged()
    }
}
