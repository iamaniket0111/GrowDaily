package com.anitech.growdaily

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AccelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
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
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: AppRepository
    lateinit var viewModel: AppViewModel
    private lateinit var navController: NavController
    var showMenu = true
    private var isReady = false

    val accentColor = MutableLiveData<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { !isReady }
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashView = splashScreenViewProvider.view
            splashView.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(SPLASH_EXIT_DURATION_MS)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { splashScreenViewProvider.remove() }
                .start()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        observeAccentColor()
        startSplashTimeout()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_main))
        setupActionBarWithNavController(navController, appBarConfiguration)

        // 👇 destination change listener
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.nav_main) {
                showMenu = true
                supportActionBar?.setDisplayShowTitleEnabled(true)
                supportActionBar?.title = getString(R.string.app_name)
            } else {
                showMenu = false
                supportActionBar?.setDisplayShowTitleEnabled(true)
                supportActionBar?.title = destination.label
            }

            invalidateOptionsMenu() // 👈 menu refresh
        }

        repository = (application as MyApp).repository
        viewModel = ViewModelProvider(
            this,
            DailyTaskViewModelFactory(repository)
        )[AppViewModel::class.java]
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_app_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val calendarItem = menu?.findItem(R.id.action_calendar)
        val settingsItem = menu?.findItem(R.id.menu_settings)
        val reorderItem  = menu?.findItem(R.id.menu_reorder_task)
        val manageTasksItem = menu?.findItem(R.id.menu_manage_repeat_tasks)

        // Find the MainFragment if we are on the Home destination
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
        val mainFragment = navHostFragment?.childFragmentManager?.fragments?.get(0) as? com.anitech.growdaily.fragment.MainFragment
        
        // Show calendar only if we are on the Home fragment AND the first page (Task List) is active
        val isTaskPage = mainFragment?.isTaskPageActive() ?: true
        calendarItem?.isVisible = showMenu && isTaskPage
        
        // Show Settings/Reorder only when showMenu is true
        settingsItem?.isVisible = showMenu
        reorderItem?.isVisible = showMenu
        manageTasksItem?.isVisible = showMenu

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.action_calendar -> {
                // Find the TaskFragment to trigger its date picker
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
                val mainFragment = navHostFragment?.childFragmentManager?.fragments?.get(0) as? com.anitech.growdaily.fragment.MainFragment
                val taskFragment = mainFragment?.getCurrentFragment() as? com.anitech.growdaily.fragment.TaskFragment
                taskFragment?.showDatePicker()
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

    private fun observeAccentColor() {
        val themePreferencesManager = (application as MyApp).themePreferencesManager
        lifecycleScope.launch {
            themePreferencesManager.accentColorFlow
                .catch {
                    accentColor.value = TaskColor.DARK_BLUE.toColorInt(this@MainActivity)
                    isReady = true
                }
                .collectLatest { colorName ->
                val taskColor = TaskColor.fromName(colorName) ?: TaskColor.DARK_BLUE
                val colorInt = taskColor.toColorInt(this@MainActivity)

                accentColor.value = colorInt
                isReady = true
            }
        }
    }

    private fun startSplashTimeout() {
        lifecycleScope.launch {
            delay(SPLASH_TIMEOUT_MS)
            isReady = true
        }
    }
}
