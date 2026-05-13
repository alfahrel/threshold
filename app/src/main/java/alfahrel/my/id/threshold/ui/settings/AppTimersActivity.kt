package alfahrel.my.id.threshold.ui.settings

import alfahrel.my.id.threshold.ui.sheet.AppTimerSheet
import alfahrel.my.id.threshold.util.BaseActivity
import alfahrel.my.id.threshold.R
import alfahrel.my.id.threshold.data.model.AppInfo
import alfahrel.my.id.threshold.data.model.AppUsageStat
import alfahrel.my.id.threshold.data.repository.UsageRepository
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class AppTimersActivity : BaseActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var usageRepository: UsageRepository

    private val adapter = AppTimersAdapter(
        onEdit = { stat, appInfo, limit -> showEditTimerSheet(stat, appInfo, limit) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_app_timers)

        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        toolbar.setNavigationOnClickListener { finish() }

        usageRepository = UsageRepository(this)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadTimers()
    }

    private fun loadTimers() {
        lifecycleScope.launch {
            val timers = usageRepository.getAppTimers()
            val items = timers.mapNotNull { (pkg, limit) ->
                val info = usageRepository.getAppInfo(pkg)
                val stat = AppUsageStat(pkg, 0L, emptyList(), emptyList())
                Triple(stat, info, limit)
            }
            adapter.submitList(items)
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showEditTimerSheet(stat: AppUsageStat, appInfo: AppInfo?, currentLimit: Int) {
        AppTimerSheet(
            stat = stat,
            appInfo = appInfo,
            currentLimit = currentLimit,
            onSave = { newLimit ->
                usageRepository.setAppTimer(stat.packageName, newLimit)
                loadTimers()
            },
            onRemove = {
                usageRepository.removeAppTimer(stat.packageName)
                loadTimers()
            }
        ).show(supportFragmentManager, "edit_timer_sheet")
    }
}

class AppTimersAdapter(
    private val onEdit: (AppUsageStat, AppInfo?, Int) -> Unit
) : RecyclerView.Adapter<AppTimersAdapter.ViewHolder>() {

    private var items: List<Triple<AppUsageStat, AppInfo?, Int>> = emptyList()

    fun submitList(list: List<Triple<AppUsageStat, AppInfo?, Int>>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_timer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (stat, info, limit) = items[position]
        holder.bind(stat, info, limit, onEdit)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        private val tvName: TextView = view.findViewById(R.id.tvAppName)
        private val tvLimit: TextView = view.findViewById(R.id.tvTimerLimit)

        fun bind(
            stat: AppUsageStat,
            info: AppInfo?,
            limit: Int,
            onEdit: (AppUsageStat, AppInfo?, Int) -> Unit
        ) {
            if (info != null && info.iconBytes.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(info.iconBytes, 0, info.iconBytes.size)
                ivIcon.setImageBitmap(bitmap)
            } else {
                ivIcon.setImageResource(R.drawable.ic_launcher_foreground)
            }
            tvName.text = info?.appName ?: stat.packageName.split(".").last()
            tvLimit.text = "${limit}m daily limit"
            itemView.setOnClickListener { onEdit(stat, info, limit) }
        }
    }
}