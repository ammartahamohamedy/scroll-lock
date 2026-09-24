package dev.scrolllock.dpc

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

class AppListAdapter(
    context: Context,
    private val onAction: (AppCatalog.AppEntry, locked: Boolean) -> Unit
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private var all = emptyList<AppCatalog.AppEntry>()
    private var visible = emptyList<AppCatalog.AppEntry>()
    private var locked = emptySet<String>()
    private var query = ""

    fun setData(data: List<AppCatalog.AppEntry>, lockedPackages: Set<String>) {
        all = data
        locked = lockedPackages
        applyFilter()
    }

    fun setQuery(value: String) {
        query = value.trim().lowercase()
        applyFilter()
    }

    fun visibleCount(): Int = visible.size

    private fun applyFilter() {
        visible = if (query.isBlank()) {
            all
        } else {
            all.filter {
                it.label.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query)
            }
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = visible.size
    override fun getItem(position: Int): AppCatalog.AppEntry = visible[position]
    override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_app_row, parent, false)
        val holder = (view.tag as? Holder) ?: Holder(view).also { view.tag = it }

        val entry = getItem(position)
        val isLocked = entry.packageName in locked

        holder.icon.setImageDrawable(entry.icon)
        holder.label.text = if (isLocked) {
            "${entry.label}  ·  LOCKED"
        } else {
            entry.label
        }
        holder.label.setTypeface(null, if (isLocked) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        holder.subtitle.text = buildString {
            append(entry.packageName)
            if (entry.isSystem) append(" · system")
            entry.protectedReason?.let { append(" · protected: $it") }
        }

        holder.action.text = if (isLocked) {
            view.context.getString(R.string.unlock)
        } else {
            view.context.getString(R.string.lock)
        }
        holder.action.isEnabled = isLocked || entry.protectedReason == null
        holder.action.setOnClickListener { onAction(entry, isLocked) }

        return view
    }

    private class Holder(view: View) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val label: TextView = view.findViewById(R.id.appLabel)
        val subtitle: TextView = view.findViewById(R.id.appSubtitle)
        val action: Button = view.findViewById(R.id.actionButton)
    }
}
