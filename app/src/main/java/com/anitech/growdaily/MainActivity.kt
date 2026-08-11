package com.anitech.growdaily

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AccelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import com.anitech.growdaily.database.repository.AppRepository
import com.anitech.growdaily.database.viewmodel.AppViewModel
import com.anitech.growdaily.database.viewmodel.DailyTaskViewModelFactory
import com.anitech.growdaily.databinding.ActivityMainBinding
import com.anitech.growdaily.enum_class.TaskColor
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val SPLASH_TIMEOUT_MS = 2_000L
        private const val SPLASH_EXIT_DURATION_MS = 220L
        private const val MIN_SPLASH_DISPLAY_MS = 600L
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: AppRepository
    lateinit var viewModel: AppViewModel
    private lateinit var navController: NavController
    private var toolbarDateDropdown: android.view.View? = null
    var showMenu = true
    private var isReady = false
    private var lastDateText: String? = null

    val accentColor = MutableLiveData<Int>()
    val todayButtonState = MutableLiveData(TodayButtonState(isVisible = false))

    data class TodayButtonState(
        val isVisible: Boolean,
        val text: String = "Today"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        val isRecreation = savedInstanceState != null
        val startTime = System.currentTimeMillis()
        
        if (isRecreation) {
            setTheme(R.style.Theme_GrowDaily_NoActionBar)
        } else {
            val splashScreen = installSplashScreen()
            splashScreen.setKeepOnScreenCondition { !isReady }
            splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
                val splashView = splashScreenViewProvider.view
                val iconView = splashScreenViewProvider.iconView

                // Elegant scale-up and slide-up animation for the logo icon
                iconView.animate()
                    .alpha(0f)
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .translationYBy(-120f)
                    .setDuration(SPLASH_EXIT_DURATION_MS)
                    .setInterpolator(AccelerateInterpolator())
                    .start()

                // Smooth fade-out animation for the splash window background
                splashView.animate()
                    .alpha(0f)
                    .setDuration(SPLASH_EXIT_DURATION_MS)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        splashScreenViewProvider.remove()
                        syncStatusBarTheme()
                    }
                    .start()
            }
        }

        super.onCreate(savedInstanceState)

        if (isRecreation) {
            isReady = true
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        syncStatusBarTheme()

        observeAccentColor(startTime, isRecreation)
        if (!isRecreation) {
            startSplashTimeout()
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_main))
        setupActionBarWithNavController(navController, appBarConfiguration)

        // 👇 destination change listener
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.nav_main) {
                showMenu = true
                // We handle toolbar/dropdown setup in onPrepareOptionsMenu
                // based on the active ViewPager page.
            } else {
                showMenu = false
                supportActionBar?.setDisplayShowTitleEnabled(true)
                supportActionBar?.title = destination.label
                removeToolbarDateDropdown()
            }

            invalidateOptionsMenu() // 👈 menu refresh
        }

        repository = (application as MyApp).repository
        viewModel = ViewModelProvider(
            this,
            DailyTaskViewModelFactory(repository)
        )[AppViewModel::class.java]
    }

    private fun setupToolbarDateDropdown() {
        if (toolbarDateDropdown == null) {
            toolbarDateDropdown = layoutInflater.inflate(R.layout.toolbar_date_dropdown, binding.toolbar, false)
            val params = androidx.appcompat.widget.Toolbar.LayoutParams(
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT,
                androidx.appcompat.widget.Toolbar.LayoutParams.MATCH_PARENT,
                android.view.Gravity.START
            )
            binding.toolbar.addView(toolbarDateDropdown, params)
            
            toolbarDateDropdown?.setOnClickListener {
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
                val mainFragment = navHostFragment?.childFragmentManager?.fragments?.get(0) as? com.anitech.growdaily.fragment.MainFragment
                val taskFragment = mainFragment?.getCurrentFragment() as? com.anitech.growdaily.fragment.TaskFragment
                taskFragment?.showDatePicker()
            }
        }
        toolbarDateDropdown?.visibility = android.view.View.VISIBLE
        
        val dateTextToSet = lastDateText ?: run {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
            val mainFragment = navHostFragment?.childFragmentManager?.fragments?.get(0) as? com.anitech.growdaily.fragment.MainFragment
            val taskFragment = mainFragment?.getCurrentFragment() as? com.anitech.growdaily.fragment.TaskFragment
            taskFragment?.getSelectedDateFormatted()
        } ?: run {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.getDefault())
            java.time.LocalDate.now().format(formatter)
        }
        updateToolbarDate(dateTextToSet)
    }

    private fun removeToolbarDateDropdown() {
        toolbarDateDropdown?.visibility = android.view.View.GONE
    }

    fun updateToolbarDate(dateText: String) {
        lastDateText = dateText
        toolbarDateDropdown?.findViewById<android.widget.TextView>(R.id.toolbarDateText)?.text = dateText
    }

    fun syncToolbarDateFromFragment() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        val mainFragment = navHostFragment?.childFragmentManager?.fragments?.get(0) as? com.anitech.growdaily.fragment.MainFragment
        val taskFragment = mainFragment?.getCurrentFragment() as? com.anitech.growdaily.fragment.TaskFragment
        taskFragment?.getSelectedDateFormatted()?.let { dateText ->
            updateToolbarDate(dateText)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_app_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val todayItem = menu?.findItem(R.id.action_today)
        val settingsItem = menu?.findItem(R.id.menu_settings)
        val reorderItem  = menu?.findItem(R.id.menu_reorder_task)
        val manageTasksItem = menu?.findItem(R.id.menu_manage_repeat_tasks)
        val aiChatItem = menu?.findItem(R.id.menu_ai_chat)

        // Find the MainFragment if we are on the Home destination
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        val mainFragment = navHostFragment?.childFragmentManager?.fragments?.get(0) as? com.anitech.growdaily.fragment.MainFragment
        
        val isTaskPage = mainFragment?.isTaskPageActive() ?: true
        val state = todayButtonState.value ?: TodayButtonState(isVisible = false)

        if (showMenu) {
            aiChatItem?.isVisible = true
            accentColor.value?.let { color ->
                aiChatItem?.icon?.setTint(color)
            }

            if (isTaskPage) {
                // TaskFragment mode: Show Dropdown, handle Today button
                supportActionBar?.setDisplayShowTitleEnabled(false)
                setupToolbarDateDropdown()

                if (state.isVisible) {
                    settingsItem?.isVisible = false
                    reorderItem?.isVisible = false
                    manageTasksItem?.isVisible = false
                    
                    todayItem?.isVisible = true
                    
                    // Use custom action view to ensure text + icon are always visible
                    if (todayItem?.actionView == null) {
                        todayItem?.setActionView(R.layout.action_bar_today_button)
                    }
                    
                    val actionView = todayItem?.actionView
                    val iconLeft = actionView?.findViewById<android.widget.ImageView>(R.id.todayIconLeft)
                    val iconRight = actionView?.findViewById<android.widget.ImageView>(R.id.todayIconRight)
                    
                    // Sync current accent color to custom view
                    accentColor.value?.let { color ->
                        iconLeft?.setColorFilter(color)
                        iconRight?.setColorFilter(color)
                    }
                    
                    val isFuture = state.text.startsWith("<")
                    iconLeft?.visibility = if (isFuture) android.view.View.VISIBLE else android.view.View.GONE
                    iconRight?.visibility = if (!isFuture) android.view.View.VISIBLE else android.view.View.GONE
                    
                    actionView?.setOnClickListener {
                        onOptionsItemSelected(todayItem)
                    }
                } else {
                    todayItem?.isVisible = false
                    settingsItem?.isVisible = true
                    reorderItem?.isVisible = true
                    manageTasksItem?.isVisible = true
                }
            } else {
                // RepeatTaskFragment mode: Show Title, hide Dropdown/Today
                removeToolbarDateDropdown()
                supportActionBar?.setDisplayShowTitleEnabled(true)
                supportActionBar?.title = getString(R.string.repeat_task_title_bar)
                
                todayItem?.isVisible = false
                settingsItem?.isVisible = true
                reorderItem?.isVisible = true
                manageTasksItem?.isVisible = true
            }
        } else {
            todayItem?.isVisible = false
            aiChatItem?.isVisible = false
            removeToolbarDateDropdown()
            
            settingsItem?.isVisible = false
            reorderItem?.isVisible = false
            manageTasksItem?.isVisible = false
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.action_today -> {
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
                val mainFragment = navHostFragment?.childFragmentManager?.fragments?.get(0) as? com.anitech.growdaily.fragment.MainFragment
                val taskFragment = mainFragment?.getCurrentFragment() as? com.anitech.growdaily.fragment.TaskFragment
                taskFragment?.scrollToToday()
                true
            }

            R.id.menu_ai_chat -> {
                navController.navigate(R.id.aiChatFragment)
                true
            }

            R.id.menu_settings -> {
                navController.navigate(R.id.settingsFragment)
                true
            }

            R.id.menu_reorder_task -> {
                navController.navigate(R.id.reorderDailyTaskFragment)
                true
            }

            R.id.menu_manage_repeat_tasks -> {
                navController.navigate(R.id.manageRepeatTasksFragment)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun observeAccentColor(startTime: Long, isRecreation: Boolean) {
        val themePreferencesManager = (application as MyApp).themePreferencesManager
        lifecycleScope.launch {
            themePreferencesManager.accentColorFlow
                .catch {
                    accentColor.value = TaskColor.DARK_BLUE.toColorInt(this@MainActivity)
                    if (!isRecreation) {
                        delayRemainingSplashTime(startTime)
                    }
                    isReady = true
                }
                .collectLatest { colorName ->
                val taskColor = TaskColor.fromName(colorName) ?: TaskColor.DARK_BLUE
                val colorInt = taskColor.toColorInt(this@MainActivity)

                accentColor.value = colorInt
                if (!isRecreation) {
                    delayRemainingSplashTime(startTime)
                }
                isReady = true
                invalidateOptionsMenu()
            }
        }
    }

    private suspend fun delayRemainingSplashTime(startTime: Long) {
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = MIN_SPLASH_DISPLAY_MS - elapsed
        if (remaining > 0) {
            delay(remaining)
        }
    }

    private fun startSplashTimeout() {
        lifecycleScope.launch {
            delay(SPLASH_TIMEOUT_MS)
            isReady = true
        }
    }

    override fun onResume() {
        super.onResume()
        syncStatusBarTheme()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncStatusBarTheme()
    }

    @Suppress("DEPRECATION")
    private fun syncStatusBarTheme() {
        val window = window
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        val isDark = isCurrentlyDarkTheme()
        val colorSurface = ContextCompat.getColor(this, R.color.app_bar_surface)
        window.statusBarColor = colorSurface

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
    }

    private fun isCurrentlyDarkTheme(): Boolean {
        val defaultMode = AppCompatDelegate.getDefaultNightMode()
        return when (defaultMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}
