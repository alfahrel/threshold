package alfahrel.my.id.threshold.ui.settings

import alfahrel.my.id.threshold.util.BaseActivity
import alfahrel.my.id.threshold.R
import alfahrel.my.id.threshold.data.model.AppInfo
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

class IgnoredAppsActivity : BaseActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var usageRepository: UsageRepository

    private val adapter = IgnoredAppsAdapter(onRemove = { packageName -> removeIgnored(packageName) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ignored_apps)

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

        loadIgnoredApps()
    }

    private fun loadIgnoredApps() {
        lifecycleScope.launch {
            val ignored = usageRepository.getIgnoredPackages()
            val items = ignored.mapNotNull { pkg -> usageRepository.getAppInfo(pkg) }
            adapter.submitList(items)
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun removeIgnored(packageName: String) {
        val current = usageRepository.getIgnoredPackages().toMutableSet()
        current.remove(packageName)
        usageRepository.setIgnoredPackages(current)
        loadIgnoredApps()
    }
}

class IgnoredAppsAdapter(
    private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<IgnoredAppsAdapter.ViewHolder>() {

    private var items: List<AppInfo> = emptyList()

    fun submitList(list: List<AppInfo>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ignored_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onRemove)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        private val tvName: TextView = view.findViewById(R.id.tvAppName)
        private val btnRemove: View = view.findViewById(R.id.btnRemoveIgnored)

        fun bind(info: AppInfo, onRemove: (String) -> Unit) {
            if (info.iconBytes.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(info.iconBytes, 0, info.iconBytes.size)
                ivIcon.setImageBitmap(bitmap)
            } else {
                ivIcon.setImageResource(R.drawable.ic_launcher_foreground)
            }
            tvName.text = info.appName
            btnRemove.setOnClickListener { onRemove(info.packageName) }
        }
    }
}