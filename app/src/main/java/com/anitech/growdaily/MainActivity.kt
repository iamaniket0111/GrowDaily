package com.anitech.growdaily

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.anitech.growdaily.database.AppRepository
import com.anitech.growdaily.database.AppViewModel
import com.anitech.growdaily.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: AppRepository
    lateinit var viewModel: AppViewModel
    private lateinit var navController: NavController
    private var showMenu = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
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
        return if (showMenu) {
            menuInflater.inflate(
                R.menu.top_app_menu,
                menu
            )
            true
        } else {
            false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.menu_settings -> {
                Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.menu_analyse -> {
                // Handle analyse
                navController
                    .navigate(R.id.nav_diary)

                true
            }


            R.id.menu_reorder_task -> {
                navController.navigate(R.id.reorderDailyTaskFragment)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}