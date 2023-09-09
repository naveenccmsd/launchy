package com.hugegreenbug.launchy

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.hugegreenbug.launchy.db.adapter.FavoriteAdapter
import com.hugegreenbug.launchy.db.database.FavoriteDatabase
import com.hugegreenbug.launchy.db.model.Favorite
import kotlin.concurrent.thread
import android.view.View.*
import java.util.concurrent.locks.ReentrantLock
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.fragment.app.FragmentTransaction
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.preference.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat

class MainActivity : AppCompatActivity() {
    private lateinit var appList: RecyclerView
    private lateinit var titleText: TextView
    private lateinit var titleClock: TextClock
    private lateinit var settingsButton: ImageView
    private lateinit var androidSettingsButton: ImageView
    private lateinit var appManager: AppManager
    private lateinit var favoriteDb: FavoriteDatabase
    private lateinit var adapter: FavoriteAdapter
    private val appDataLock = ReentrantLock()
    private val appData: ArrayList<App> = ArrayList()
    private val colCount = 5
    private lateinit var sh:SharedPreferences
        @Volatile
    private var allApps: Boolean = false

    private val getResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
                result ->
            run {
                if (result.resultCode == Activity.RESULT_OK) {
                    // There are no request codes
                    val data: Intent? = result.data
                    Log.d("TAG", "data: $data ")
                }
                focusAppsLongDelay()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        titleText = findViewById(R.id.title)
        titleClock = findViewById(R.id.textClock)
        settingsButton = findViewById(R.id.launcherSettings)
        androidSettingsButton = findViewById(R.id.androidSettings)
        settingsButton.visibility = INVISIBLE
        sh = getSharedPreferences(launchPrefs, MODE_PRIVATE)
        appManager = AppManager(applicationContext)
        favoriteDb = FavoriteDatabase.getAppDatabase(this)!!
        initAndroidSettingsButton()
        initAppList()

        val runOnboard = sh.getBoolean(showOnboard, true)
        if (runOnboard) {
            val intent = Intent(this, OnboardingActivity::class.java)
            getResult.launch(intent)
            val prefEditor: SharedPreferences.Editor = sh.edit()
            prefEditor.putBoolean(showOnboard, false)
            prefEditor.apply()
        }

        val dimValue:String = sh.getString(backgroundDim, "Light") as String
        setBackgroundDim(dimValue)
        updateApps(false)
    }

    private fun setBackgroundDim(value:String) {
        when (value) {
            "None" -> {
                window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.transparentbg, null)
                )
            }
            "Light" -> {
                window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.blacktransparentbg_light, null)
                )
            }
            "Medium" -> {
                window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.blacktransparentbg_medium, null)
                )
            }
            "Dark" -> {
                window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.blacktransparentbg_dark, null)
                )
            }
            else -> {
                window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.blacktransparentbg_light, null)
                )
            }
        }
    }
    private fun startSettings() {
        val fragment: SettingsFragment? =
            supportFragmentManager.findFragmentByTag(SettingsFragment.settingsTagFragment) as SettingsFragment?
        if (fragment == null) {
            val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
            ft.setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            ft.add(R.id.settings, SettingsFragment(), SettingsFragment.settingsTagFragment)
                .commit()
        }
    }

    private fun initAndroidSettingsButton() {
        androidSettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
        settingsButton.setOnClickListener {
            val loadApps:Boolean = sh.getBoolean( loadApps, false)
            if(loadApps){
                sh.edit().putBoolean(Companion.loadApps,false).apply()
                updateApps(false)
            }else {
                startSettings()
            }
        }
        settingsButton.setOnKeyListener { _, code, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }

            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusApps()
                return@setOnKeyListener true
            }

            return@setOnKeyListener false
        }

        settingsButton.clearFocus()
    }

    private fun focusAppsLongDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            focusApps()
        }, 2400)
    }

    private fun focusAppsDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            focusApps()
        }, 400)
    }

    private fun focusApps() {
        runOnUiThread {
            appList.requestFocus()
            settingsButton.visibility = VISIBLE
        }
    }

    private fun initAppList() {
        adapter = FavoriteAdapter(applicationContext,appManager, appData)
        adapter.itemLongClickListener = { app, itemView -> addRemoveFavorites(app, itemView) }
        adapter.setHasStableIds(true)
        appList = findViewById(R.id.appList)
        appList.layoutManager = PredictiveGridLayoutManager(this, colCount)
        appList.adapter = adapter
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateApps(hideAppsVar: Boolean) {
        thread {
            var editIcon = R.drawable.ic_edit
            val loadApps:Boolean = sh.getBoolean( loadApps, false)
            if(loadApps) {
                editIcon = R.drawable.ic_check
            }
            settingsButton.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    editIcon,
                    null
                )
            )
            val hideApps = if (loadApps ) true else hideAppsVar
            appDataLock.lock()
            var favs = favoriteDb.favorites().getAllFavorites()

            val allAppsList = appManager.getLaunchableApps(sh.getBoolean(showSideload, false))
            val newAppList: ArrayList<App> = ArrayList()


            if (favs.isEmpty() && !hideApps) {
                for (a in allAppsList) {
                    if (a.packageName == packageName) {
                        continue
                    }
                    val favorite = Favorite(packageName = a.packageName)
                    favoriteDb.favorites().insert(favorite)
                }

                favs = favoriteDb.favorites().getAllFavorites()
            } else {
                //Clean DB

                var changed = false
                for (f in favs) {
                    var packageFound = false
                    for (a in allAppsList) {
                        if (a.packageName == f.packageName && a.packageName != packageName) {
                            packageFound = true
                            break
                        }
                    }

                    if (!packageFound) {
                        favoriteDb.favorites().delete(f.packageName)
                        changed = true
                    }
                }

                if (changed) {
                    favs = favoriteDb.favorites().getAllFavorites()
                }
            }

            for (a in allAppsList) {
                for (f in favs) {
                    if (a.packageName == f.packageName) {
                        a.favorite = true
                        break
                    }
                }

                if (hideApps && a.packageName != packageName) {
                    newAppList.add(a)
                } else if (!hideApps && a.favorite) {
                    a.favorite = false
                    newAppList.add(a)
                }
            }

            newAppList.sortWith(compareBy( { app -> app.sideLoad},{ app -> app.label }))
            if (hideApps == allApps && appData.isEqual(newAppList)) {
                appDataLock.unlock()
                return@thread
            }

            allApps = hideApps

            runOnUiThread {
                if (allApps) {
                    titleText.text = getString(R.string.all_apps)
                } else {
                    titleText.text = getString(R.string.favorites)
                }
                appData.clear()
                adapter.notifyDataSetChanged()
                appData.addAll(newAppList)
                adapter.notifyDataSetChanged()
                focusAppsDelay()
                if (appData.size > 0) {
                    appList.scrollToPosition(0)
                }
            }

            appDataLock.unlock()
        }
    }

    private fun ArrayList<App>.isEqual(comparable: ArrayList<App>): Boolean {
        var isChanged = true

        if (this.size == comparable.size) {
            for (index in 0 until comparable.size) {
                if (this[index].packageName != comparable[index].packageName) {
                    isChanged = false
                    break
                }
            }
        } else {
            isChanged = false
        }

        return isChanged
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val fragment: SettingsFragment? =
                    supportFragmentManager.findFragmentByTag(
                        SettingsFragment.settingsTagFragment
                    ) as SettingsFragment?
                if (fragment != null && fragment.isAdded) {
                    val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
                    ft.setCustomAnimations(android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right)
                    ft.remove(fragment).commit()
                    focusApps()

                    return true
                }
                false
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun addRemoveFavorites(app: App, itemView: RelativeLayout): Boolean {
        val favorite = Favorite(packageName = app.packageName)

        if (!allApps) {
            return false
        }

        thread {
            val fav = favoriteDb.favorites().getFavorite(favorite.packageName)
            if (fav == null) {
                favoriteDb.favorites().insert(favorite)
                runOnUiThread {
                    itemView.visibility = VISIBLE
                 }
            } else {
                favoriteDb.favorites().delete(favorite.packageName)
                runOnUiThread {
                    itemView.visibility = INVISIBLE
                }
            }
        }

        return true
    }

    private fun removeFrag() {
        val fragment: SettingsFragment? =
            supportFragmentManager.findFragmentByTag(SettingsFragment.settingsTagFragment) as SettingsFragment?
        if (fragment != null && fragment.isAdded) {
            val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
            ft.setCustomAnimations(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            ft.remove(fragment).commit()
            focusApps()
        }
    }

    override fun onBackPressed() {
        val fragment: SettingsFragment? =
            supportFragmentManager.findFragmentByTag(SettingsFragment.settingsTagFragment) as SettingsFragment?
        if (fragment != null && fragment.isAdded) {
            removeFrag()
        } else {
            updateApps(false)
        }
    }

    override fun onResume() {
        super.onResume()
        updateApps(allApps)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (favoriteDb.isOpen) {
            favoriteDb.close()
        }
    }

    companion object {
        const val launchPrefs:String="LAUNCHY"
        const val showOnboard:String="onboard"
        const val backgroundDim:String="dim"
        var loadApps:String="loadApps"
        var showSideload:String="showSideload"
    }
}
