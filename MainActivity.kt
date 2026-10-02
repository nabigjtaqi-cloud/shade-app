package com.shade.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Shade: a home-screen launcher that hides chosen apps/games behind a PIN-protected vault. */
class MainActivity : Activity() {

    data class AppItem(val label: String, val pkg: String, val icon: Drawable)

    private lateinit var prefs: SharedPreferences
    private lateinit var grid: GridView
    private lateinit var title: TextView
    private lateinit var vaultBtn: Button
    private var vaultMode = false
    private var all = listOf<AppItem>()
    private var shown = listOf<AppItem>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun hidden(): MutableSet<String> = prefs.getStringSet("hidden", emptySet())!!.toMutableSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("shade", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F1A"))
            setPadding(dp(16), dp(44), dp(16), dp(12))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        title = TextView(this).apply { setTextColor(Color.WHITE); textSize = 24f }
        vaultBtn = Button(this).apply { text = "Vault"; setOnClickListener { toggleVault() } }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(vaultBtn)

        grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(18)
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(header)
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        if (prefs.getString("pin", null) == null) askNewPin()
    }

    override fun onResume() { super.onResume(); refresh() }

    // Auto-lock: leaving the app always closes the vault.
    override fun onStop() { super.onStop(); vaultMode = false }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (vaultMode) { vaultMode = false; refresh() } }

    private fun refresh() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        all = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        val h = hidden()
        shown = all.filter { (it.pkg in h) == vaultMode }
        title.text = if (vaultMode) "Vault (${shown.size})" else "Shade"
        vaultBtn.text = if (vaultMode) "Close" else "Vault"
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, p: ViewGroup?): View {
                val a = shown[i]
                return LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(ImageView(context).apply { setImageDrawable(a.icon) }, LinearLayout.LayoutParams(dp(52), dp(52)))
                    addView(TextView(context).apply {
                        text = a.label; setTextColor(Color.WHITE); textSize = 11f
                        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                    })
                    setOnClickListener {
                        packageManager.getLaunchIntentForPackage(a.pkg)?.let { startActivity(it) }
                    }
                    setOnLongClickListener { confirmToggle(a); true }
                }
            }
        }
    }

    private fun confirmToggle(a: AppItem) {
        val hide = !vaultMode
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setMessage(if (hide) "Hide this app from your home screen?" else "Show this app on your home screen again?")
            .setPositiveButton(if (hide) "Hide" else "Unhide") { _, _ ->
                val h = hidden()
                if (hide) h.add(a.pkg) else h.remove(a.pkg)
                prefs.edit().putStringSet("hidden", h).apply()
                refresh()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun toggleVault() {
        if (vaultMode) { vaultMode = false; refresh() }
        else askPin("Enter PIN") { vaultMode = true; refresh() }
    }

    private fun pinField() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        hint = "4–6 digits"
    }

    private fun askPin(msg: String, onOk: () -> Unit) {
        val f = pinField()
        AlertDialog.Builder(this).setTitle(msg).setView(f)
            .setPositiveButton("Open") { _, _ ->
                if (f.text.toString() == prefs.getString("pin", null)) onOk()
                else Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun askNewPin() {
        val f = pinField()
        AlertDialog.Builder(this).setTitle("Create your vault PIN").setView(f).setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val p = f.text.toString()
                if (p.length >= 4) prefs.edit().putString("pin", p).apply() else askNewPin()
            }.show()
    }
}
