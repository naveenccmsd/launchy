package com.hugegreenbug.launchy

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.leanback.preference.LeanbackSettingsFragmentCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.codekidlabs.storagechooser.StorageChooser
import java.io.File


class SettingsFragment : LeanbackSettingsFragmentCompat() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        view?.requestFocus()

        return view
    }

    override fun onPreferenceStartInitialScreen() {
        startPreferenceFragment(LauncherFragment())
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val args = pref.extras
        val f: Fragment = childFragmentManager.fragmentFactory.instantiate(
            requireActivity().classLoader, pref.fragment
        )
        f.arguments = args
        childFragmentManager.beginTransaction().add(f, preferenceFragTag).addToBackStack(null).commit()
        if (f is PreferenceFragmentCompat
            || f is PreferenceDialogFragmentCompat
        ) {
            startPreferenceFragment(f)
        } else {
            startImmersiveFragment(f)
        }
        return true
    }

    override fun onPreferenceStartScreen(
        caller: PreferenceFragmentCompat?,
        pref: PreferenceScreen
    ): Boolean {
        val fragment: Fragment = LauncherFragment()
        val args = Bundle(1)
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
        fragment.arguments = args
        startPreferenceFragment(fragment)
        return true
    }

    companion object {
        const val settingsTagFragment:String="LAUNCHER_SETTINGS_FRAG"
        const val preferenceFragTag:String = "preferenceFragTag"

    }
}

/**
 * The fragment that is embedded in SettingsFragment
 */
class LauncherFragment : LeanbackPreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val hideApps: Preference? = findPreference(getString(R.string.hide_apps)) as Preference?
        hideApps?.setOnPreferenceClickListener {
            activity?.onBackPressed()
            context?.getSharedPreferences(MainActivity.launchPrefs, MODE_PRIVATE)?.edit()
                ?.putBoolean(MainActivity.loadApps,true)?.apply()
                val intent = Intent(requireActivity(), MainActivity::class.java)
                startActivity(intent)
            return@setOnPreferenceClickListener true
        }
        val showSideload: Preference? = findPreference(getString(R.string.show_sideload)) as Preference?
        if (showSideload != null) {
            showSideload.onPreferenceChangeListener = Preference.OnPreferenceChangeListener  { _, newValue ->
                activity?.onBackPressed()
                val sh = context?.getSharedPreferences(MainActivity.launchPrefs, MODE_PRIVATE)
                val prefEditor: SharedPreferences.Editor? = sh?.edit()
                prefEditor?.putBoolean(MainActivity.loadApps, newValue as Boolean)
                prefEditor?.putBoolean(MainActivity.showSideload, newValue as Boolean)
                prefEditor?.apply()

                val intent = Intent(requireActivity(), MainActivity::class.java)
                startActivity(intent)
                true
            }
        }
        val chooser = StorageChooser.Builder()
            .withActivity(this.activity)
            .withFragmentManager(this.activity?.fragmentManager  )
            .withMemoryBar(true)
            .build()
        val listPreference: ListPreference? =
            findPreference(getString(R.string.pref_background)) as ListPreference?
        if (listPreference != null) {
            listPreference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    var wall: Drawable? = null
                    when (newValue) {
                        "Default" -> {
                            wall = ResourcesCompat.getDrawable(resources, R.drawable.blackbg, null)
                        }
                        "Local" -> {
                            chooser.show()
                            // get path that the user has chosen
                            chooser.setOnSelectListener { path ->
                                val imgFile = File(path)
                                if(imgFile.exists()){
                                    val myBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                                    this.activity?.let { BitmapUtils.setWallpaper(it,myBitmap) }
                                    true
                                }
                            }
                        }
                        "RainbowPainting" -> {
                            wall = ResourcesCompat.getDrawable(resources, R.drawable.rainbow, null)
                        }
                        "kidsRainbow" -> {
                            wall = ResourcesCompat.getDrawable(resources, R.drawable.kids_rainbow, null)
                        }
                        "Black" -> {
                            wall = ResourcesCompat.getDrawable(resources, R.drawable.blackbg, null)
                        }
                        "DarkGrey" -> {
                            wall = ResourcesCompat.getDrawable(resources, R.drawable.greybg, null)
                        }
                        "UnsplashNature" -> {
                            startUnsplashActivity(UnsplashActivity.nature)
                        }
                        "UnsplashAnimals" -> {
                            startUnsplashActivity(UnsplashActivity.animals)
                        }
                        "UnsplashTextures" -> {
                            startUnsplashActivity(UnsplashActivity.textures)
                        }
                        "UnsplashDroneCaptures" -> {
                            startUnsplashActivity(UnsplashActivity.drone)
                        }
                        "UnsplashWallpapers" -> {
                            startUnsplashActivity(UnsplashActivity.wallpapers)
                        }
                        "UnsplashEditorial" -> {
                            startUnsplashActivity(UnsplashActivity.editorial)
                        }
                        else -> {
                            wall = ResourcesCompat.getDrawable(resources, R.drawable.blackbg, null)
                        }
                    }

                    if (wall != null) {
                        this.activity?.let { BitmapUtils.setWallpaper(wall!!, it) }
                    }

                    true
                }
        }

        val dimPreference: ListPreference? =
            findPreference(getString(R.string.pref_dim_background)) as ListPreference?
        if (dimPreference != null) {
            if (dimPreference.value == null) {
                // to ensure we don't get a null value
                // set first value by default
                val defaultValue = "Light"
                dimPreference.value = defaultValue
                setBackgroundDim(defaultValue)
            }
            setBackgroundDim(dimPreference.value)
            dimPreference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    setBackgroundDim(newValue as String)
                    true
                }
        }
    }

    private fun setBackgroundDim(value:String) {
        when (value) {
            "None" -> {
                activity?.window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.transparentbg, null)
                )
            }
            "Light" -> {
                activity?.window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.blacktransparentbg_light, null)
                )
            }
            "Medium" -> {
                activity?.window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.blacktransparentbg_medium, null)
                )
            }
            "Dark" -> {
                activity?.window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.blacktransparentbg_dark, null)
                )
            }
            else -> {
                activity?.window?.setBackgroundDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.blacktransparentbg_light, null)
                )
            }
        }

        val sh = context?.getSharedPreferences(MainActivity.launchPrefs, MODE_PRIVATE)
        val prefEditor: SharedPreferences.Editor? = sh?.edit()
        prefEditor?.putString(MainActivity.backgroundDim, value)
        prefEditor?.apply()
    }

    private fun startUnsplashActivity(value:String) {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this.activity, UnsplashActivity::class.java)
            intent.putExtra(UnsplashActivity.unsplashKey, value)
            startActivity(intent)
        }, 1200)
    }


    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return super.onPreferenceTreeClick(preference)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        view?.requestFocus()

        return view
    }
}






