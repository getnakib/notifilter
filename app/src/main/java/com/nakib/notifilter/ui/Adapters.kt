package com.nakib.notifilter.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nakib.notifilter.R
import com.nakib.notifilter.data.AppStat
import com.nakib.notifilter.data.Record
import java.util.Date

/** Friendly app name. The service stores the label, but older rows (or a
 *  label lookup failure) can hold the package name — resolve it here.
 *  Needs QUERY_ALL_PACKAGES (declared in the manifest) on Android 11+,
 *  otherwise PackageManager cannot see other apps and throws. Last resort:
 *  the last package segment, capitalised ("Messaging"), never the raw
 *  package name. */
fun friendlyAppName(context: Context, record: Record): String {
    val stored = record.appName
    val looksLikePackage = stored.contains(".") && stored == stored.lowercase() && !stored.contains(" ")
    if (stored.isNotBlank() && stored != record.pkg && !looksLikePackage) return stored
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(record.pkg, 0)
        val label = pm.getApplicationLabel(info).toString().trim()
        if (label.isNotBlank() && label != record.pkg) label else lastSegmentName(record.pkg)
    } catch (_: Exception) {
        lastSegmentName(record.pkg)
    }
}

/** Last package segment, capitalised — e.g. "com.google.android.apps.messaging"
 *  -> "Messaging". Only a last-resort display name when the real label
 *  cannot be resolved; never show the full package name. */
fun lastSegmentName(pkg: String): String {
    val seg = pkg.substringAfterLast(".").ifBlank { pkg }
    return seg.replaceFirstChar { it.uppercaseChar() }
}

/** Load the originating app's real icon into [icon]; on failure show the
 *  letter initial in [initial] instead. */
fun bindAppIcon(context: Context, pkg: String, appName: String, icon: ImageView, initial: TextView) {
    var drawable: Drawable? = null
    try {
        drawable = context.packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) {
        drawable = null
    }
    if (drawable != null) {
        icon.setImageDrawable(drawable)
        icon.visibility = View.VISIBLE
        initial.visibility = View.GONE
    } else {
        icon.visibility = View.GONE
        initial.visibility = View.VISIBLE
        initial.text = appName.firstOrNull()?.uppercase() ?: "?"
    }
}

/** Tag styles from the approved preview. */
enum class TagKind { OTP, PASS, IMAGE, SPAM, NEUTRAL }

fun styleTag(context: Context, tag: TextView, kind: TagKind) {
    val (bg, fg, dot) = when (kind) {
        TagKind.OTP -> Triple(R.color.md_secondary_container, R.color.md_on_secondary_container, R.color.md_primary)
        TagKind.PASS -> Triple(R.color.md_tertiary_container, R.color.md_on_tertiary_container, R.color.md_ok)
        TagKind.IMAGE -> Triple(R.color.md_primary_container, R.color.md_on_primary_container, R.color.md_primary)
        TagKind.SPAM -> Triple(R.color.md_error_container, R.color.md_on_error_container, R.color.md_error)
        TagKind.NEUTRAL -> Triple(R.color.md_container_high, R.color.md_on_surface, R.color.md_outline_strong)
    }
    tag.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bg))
    tag.setTextColor(ContextCompat.getColor(context, fg))
    tag.compoundDrawableTintList = ColorStateList.valueOf(ContextCompat.getColor(context, dot))
}

/** Shown notifications. OTP rows show the code as one unbroken string.
 *  Each card has a menu with Mark as spam, Dismiss. */
class NotificationAdapter(
    private var items: List<Record>,
    private val onMarkSpam: (Record) -> Unit = {},
    private val onDismiss: (Record) -> Unit = {}
) : RecyclerView.Adapter<NotificationAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val initial: TextView = view.findViewById(R.id.app_initial)
        val appName: TextView = view.findViewById(R.id.app_name)
        val time: TextView = view.findViewById(R.id.time)
        val title: TextView = view.findViewById(R.id.title)
        val body: TextView = view.findViewById(R.id.body)
        val tag: TextView = view.findViewById(R.id.tag)
        val otpRow: View = view.findViewById(R.id.otp_row)
        val otpCode: TextView = view.findViewById(R.id.otp_code)
        val mediaRow: View = view.findViewById(R.id.media_row)
        val menuButton: ImageButton = view.findViewById(R.id.card_menu_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val name = friendlyAppName(context, item)
        bindAppIcon(context, item.pkg, name, holder.icon, holder.initial)
        holder.appName.text = name
        holder.time.text = DateFormat.getTimeFormat(context).format(Date(item.time))
        holder.title.text = item.title
        holder.body.text = item.body

        // Tag + media row.
        val (tagText, kind) = when {
            item.isOtp -> "OTP · Instant" to TagKind.OTP
            item.hasImage -> "Image · Checked" to TagKind.IMAGE
            item.reason?.startsWith("AI") == true -> "AI · Passed" to TagKind.PASS
            else -> "Shown" to TagKind.NEUTRAL
        }
        holder.tag.text = tagText
        styleTag(context, holder.tag, kind)
        holder.mediaRow.visibility = if (item.hasImage) View.VISIBLE else View.GONE

        if (item.otp != null) {
            holder.otpRow.visibility = View.VISIBLE
            // Show the code as one unbroken string — never chunk it.
            holder.otpCode.text = item.otp
        } else {
            holder.otpRow.visibility = View.GONE
        }

        holder.menuButton.setOnClickListener { anchor ->
            val popup = PopupMenu(context, anchor)
            popup.menu.add(Menu.NONE, 2, Menu.NONE, context.getString(R.string.mark_as_spam))
            popup.menu.add(Menu.NONE, 3, Menu.NONE, context.getString(R.string.dismiss))
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    2 -> onMarkSpam(item)
                    3 -> onDismiss(item)
                }
                true
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<Record>) {
        items = newItems
        notifyDataSetChanged()
    }
}

/** Blocked notifications. Per-card menu: Restore / Always allow / Delete. */
class BlockedAdapter(
    private var items: List<Record>,
    private val onRestore: (Record) -> Unit,
    private val onAllow: (Record) -> Unit,
    private val onDelete: (Record) -> Unit
) : RecyclerView.Adapter<BlockedAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val initial: TextView = view.findViewById(R.id.app_initial)
        val appName: TextView = view.findViewById(R.id.app_name)
        val time: TextView = view.findViewById(R.id.time)
        val title: TextView = view.findViewById(R.id.title)
        val body: TextView = view.findViewById(R.id.body)
        val reason: TextView = view.findViewById(R.id.reason)
        val mediaRow: View = view.findViewById(R.id.media_row)
        val menuButton: ImageButton = view.findViewById(R.id.card_menu_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val name = friendlyAppName(context, item)
        bindAppIcon(context, item.pkg, name, holder.icon, holder.initial)
        holder.appName.text = name
        holder.time.text = DateFormat.getTimeFormat(context).format(Date(item.time))
        holder.title.text = item.title
        holder.body.text = item.body
        holder.reason.text = item.reason ?: "Blocked"
        styleTag(context, holder.reason, TagKind.SPAM)
        holder.mediaRow.visibility = if (item.hasImage) View.VISIBLE else View.GONE

        holder.menuButton.setOnClickListener { anchor ->
            val popup = PopupMenu(context, anchor)
            popup.menu.add(Menu.NONE, 1, Menu.NONE, context.getString(R.string.restore_to_inbox))
            popup.menu.add(Menu.NONE, 2, Menu.NONE, context.getString(R.string.always_allow_sender))
            val deleteTitle = SpannableString(context.getString(R.string.delete_forever))
            deleteTitle.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.md_error)),
                0, deleteTitle.length, 0
            )
            popup.menu.add(Menu.NONE, 3, Menu.NONE, deleteTitle)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> onRestore(item)
                    2 -> onAllow(item)
                    3 -> onDelete(item)
                }
                true
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<Record>) {
        items = newItems
        notifyDataSetChanged()
    }
}

/** One row per app in Analytics — real vs spam split bar, sorted by total. */
class AnalyticsAdapter(
    private val items: List<AppStat>
) : RecyclerView.Adapter<AnalyticsAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val initial: TextView = view.findViewById(R.id.app_initial)
        val appName: TextView = view.findViewById(R.id.app_name)
        val total: TextView = view.findViewById(R.id.total)
        val realNums: TextView = view.findViewById(R.id.real_nums)
        val spamNums: TextView = view.findViewById(R.id.spam_nums)
        val realSeg: View = view.findViewById(R.id.real_seg)
        val spamSeg: View = view.findViewById(R.id.spam_seg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analytics, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val name = friendlyAppName(context, Record(pkg = item.pkg, appName = item.appName, title = "", body = ""))
        bindAppIcon(context, item.pkg, name, holder.icon, holder.initial)
        holder.appName.text = name
        holder.total.text = context.getString(R.string.analytics_total, item.total)
        holder.realNums.text = "${item.real} · ${item.realPct}%"
        holder.spamNums.text = "${item.spam} · ${item.spamPct}%"
        // Split bar: real (green) vs spam (red), weighted by share.
        val realWeight = item.realPct.toFloat().coerceAtLeast(0f)
        val spamWeight = item.spamPct.toFloat().coerceAtLeast(0f)
        holder.realSeg.layoutParams =
            (holder.realSeg.layoutParams as LinearLayout.LayoutParams).apply { weight = realWeight }
        holder.spamSeg.layoutParams =
            (holder.spamSeg.layoutParams as LinearLayout.LayoutParams).apply { weight = spamWeight }
        holder.realSeg.visibility = if (realWeight > 0f) View.VISIBLE else View.GONE
        holder.spamSeg.visibility = if (spamWeight > 0f) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = items.size
}
