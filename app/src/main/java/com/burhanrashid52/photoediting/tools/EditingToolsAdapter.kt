package com.burhanrashid52.photoediting.tools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.burhanrashid52.photoediting.R

/**
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @version 0.1.2
 * @since 5/23/2018
 */
class EditingToolsAdapter(
    private val mOnItemSelected: (ToolType) -> Unit
) : RecyclerView.Adapter<EditingToolsAdapter.ViewHolder>() {
    private val mToolList: List<ToolModel> = listOf(
        ToolModel("Shape", R.drawable.ic_oval, ToolType.SHAPE),
        ToolModel("Text", R.drawable.ic_text, ToolType.TEXT),
        ToolModel("Eraser", R.drawable.ic_eraser, ToolType.ERASER),
        ToolModel("Filter", R.drawable.ic_photo_filter, ToolType.FILTER),
        ToolModel("Emoji", R.drawable.ic_insert_emoticon, ToolType.EMOJI),
        ToolModel("Sticker", R.drawable.ic_sticker, ToolType.STICKER)
    )

    inner class ToolModel(
        val mToolName: String,
        val mToolIcon: Int,
        val mToolType: ToolType
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.row_editing_tools, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(mToolList[position])
    }

    override fun getItemCount(): Int = mToolList.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgToolIcon: ImageView = itemView.findViewById(R.id.imgToolIcon)
        private val txtTool: TextView = itemView.findViewById(R.id.txtTool)

        fun bind(item: ToolModel) {
            txtTool.text = item.mToolName
            imgToolIcon.setImageResource(item.mToolIcon)
            itemView.setOnClickListener { mOnItemSelected(item.mToolType) }
        }
    }
}