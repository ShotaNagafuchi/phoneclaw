package com.example.universal

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentObjectActivity : AppCompatActivity() {

    private lateinit var store: SharedObjectStore
    private lateinit var objectsRecyclerView: RecyclerView
    private lateinit var execLogTextView: android.widget.TextView
    private lateinit var adapter: UnifiedObjectAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_objects)

        store = SharedObjectStore(this)
        objectsRecyclerView = findViewById(R.id.objectsRecyclerView)
        execLogTextView = findViewById(R.id.execLogTextView)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        adapter = UnifiedObjectAdapter(emptyList()) { obj ->
            appendLog("Selected: ${obj.id} (${obj.type})")
        }
        objectsRecyclerView.layoutManager = LinearLayoutManager(this)
        objectsRecyclerView.adapter = adapter

        findViewById<com.google.android.material.button.MaterialButton>(R.id.approveButton)
            .setOnClickListener {
                appendLog("Approve tapped")
                Toast.makeText(this, "Approve", Toast.LENGTH_SHORT).show()
            }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.editButton)
            .setOnClickListener {
                appendLog("Edit tapped")
                Toast.makeText(this, "Edit", Toast.LENGTH_SHORT).show()
            }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.batchButton)
            .setOnClickListener {
                appendLog("Batch Run tapped")
                loadObjects()
            }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.undoButton)
            .setOnClickListener {
                if (store.canUndo()) {
                    store.undo()
                    appendLog("Undo performed")
                    loadObjects()
                } else {
                    appendLog("Nothing to undo")
                }
            }

        requestPermissionsIfNeeded()
        loadObjects()
    }

    private fun loadObjects() {
        scope.launch {
            appendLog("Loading objects...")
            val list = withContext(Dispatchers.IO) {
                val contacts = ObjectProviders.listContacts(this@AgentObjectActivity, 50)
                val events = ObjectProviders.listCalendarEvents(this@AgentObjectActivity, limit = 30)
                val notifications = NotificationCaptureService.getRecentNotifications(30)
                contacts + events + notifications
            }
            adapter.updateList(list)
            appendLog("Loaded ${list.size} objects")
        }
    }

    private fun appendLog(msg: String) {
        val current = execLogTextView.text.toString()
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        execLogTextView.text = TextUtils.concat("[$timestamp] $msg\n", current)
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val perms = arrayOf(
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.READ_CALENDAR
            )
            val needed = perms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), 2000)
            }
        }
        if (!isNotificationServiceEnabled()) {
            appendLog("Enable Notification access in Settings for full object sync")
            try {
                startActivity(android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Log.e(TAG, "Could not open notification settings: ${e.message}")
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { it.contains(pkgName) }
    }

    companion object {
        private const val TAG = "AgentObjectActivity"
    }
}

class UnifiedObjectAdapter(
    private var items: List<UnifiedObject>,
    private val onItemClick: (UnifiedObject) -> Unit
) : RecyclerView.Adapter<UnifiedObjectAdapter.ViewHolder>() {

    class ViewHolder(val view: android.view.View) : RecyclerView.ViewHolder(view) {
        val typeText: android.widget.TextView = view.findViewById(R.id.objectTypeText)
        val titleText: android.widget.TextView = view.findViewById(R.id.objectTitleText)
        val detailText: android.widget.TextView = view.findViewById(R.id.objectDetailText)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_unified_object, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val obj = items[position]
        holder.typeText.text = obj.type.name
        holder.titleText.text = when (obj.type) {
            ObjectType.Contact -> obj.payload.get("displayName")?.asString ?: obj.id
            ObjectType.Event -> obj.payload.get("title")?.asString ?: obj.id
            ObjectType.Notification -> obj.payload.get("title")?.asString ?: obj.payload.get("text")?.asString ?: obj.id
            else -> obj.id
        }
        holder.detailText.text = when (obj.type) {
            ObjectType.Contact -> obj.payload.getAsJsonArray("phones")?.let { arr ->
                if (arr.size() > 0) arr.get(0).asString else ""
            } ?: ""
            ObjectType.Event -> obj.payload.get("location")?.asString ?: ""
            ObjectType.Notification -> obj.payload.get("text")?.asString ?: obj.payload.get("packageName")?.asString ?: ""
            else -> ""
        }
        holder.view.setOnClickListener { onItemClick(obj) }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<UnifiedObject>) {
        items = newItems
        notifyDataSetChanged()
    }
}
