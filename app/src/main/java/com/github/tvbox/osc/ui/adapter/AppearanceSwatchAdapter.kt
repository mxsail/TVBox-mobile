package com.github.tvbox.osc.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.ThemeManager
import com.google.android.material.color.MaterialColors

/**
 * 主题颜色色板适配器: 动态取色(渐变圆) + 内置预设色(实色圆), 选中项描边高亮。
 */
class AppearanceSwatchAdapter(
    private var selectedId: String,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<AppearanceSwatchAdapter.Holder>() {

    private class Swatch(val id: String, val label: String, val colorRes: Int)

    private val items: List<Swatch> = buildList {
        add(Swatch(ThemeManager.COLOR_DYNAMIC, "动态取色", 0))
        ThemeManager.PRESETS.forEach { add(Swatch(it.id, it.label, it.swatchRes)) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val circle: View = view.findViewById(R.id.swatchCircle)
        val ring: View = view.findViewById(R.id.swatchRing)
        val label: TextView = view.findViewById(R.id.swatchLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_appearance_swatch, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val swatch = items[position]
        val context = holder.itemView.context
        if (swatch.colorRes == 0) {
            holder.circle.setBackgroundResource(R.drawable.bg_swatch_dynamic)
        } else {
            holder.circle.setBackgroundResource(R.drawable.bg_circle_solid)
            holder.circle.backgroundTintList =
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, swatch.colorRes))
        }
        val selected = swatch.id == selectedId
        holder.ring.visibility = if (selected) View.VISIBLE else View.GONE
        holder.label.text = swatch.label
        // 从主题 attr 取色, 动态取色/预设色板下跟随当前 colorPrimary
        // 注意: colorPrimary 定义在 appcompat, colorOnSurfaceVariant 定义在 material, R 来源不同
        val labelAttr = if (selected) {
            androidx.appcompat.R.attr.colorPrimary
        } else {
            com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        holder.label.setTextColor(MaterialColors.getColor(holder.itemView, labelAttr))
        holder.itemView.setOnClickListener {
            if (selectedId == swatch.id) return@setOnClickListener
            val oldPos = items.indexOfFirst { it.id == selectedId }
            selectedId = swatch.id
            if (oldPos >= 0) notifyItemChanged(oldPos)
            notifyItemChanged(position)
            onClick(swatch.id)
        }
    }

    fun selectedId(): String = selectedId
}
