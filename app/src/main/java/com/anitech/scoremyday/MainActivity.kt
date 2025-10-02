package com.anitech.scoremyday

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anitech.scoremyday.adapter.ConditionListAdapter
import com.anitech.scoremyday.data_class.ConditionEntity
import com.anitech.scoremyday.database.AppRepository
import com.anitech.scoremyday.database.AppViewModel
import com.anitech.scoremyday.databinding.ActivityMainBinding
import com.anitech.scoremyday.fragment.HomeFragment
import com.anitech.scoremyday.fragment.MainFragment


class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: AppRepository
    lateinit var viewModel: AppViewModel
    private var isSelectionMode = false
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
                if (isSelectionMode) R.menu.home_menu else R.menu.top_app_menu,
                menu
            )
            true
        } else {
            false
        }
    }

    fun setSelectionMode(enable: Boolean) {
        isSelectionMode = enable
        supportActionBar?.setDisplayHomeAsUpEnabled(enable)
        invalidateOptionsMenu() // This will trigger onCreateOptionsMenu to be called again
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val mainFragment =
            navHostFragment.childFragmentManager.fragments.firstOrNull() as? MainFragment

        val currentFragment = mainFragment?.getCurrentFragment()

        if (isSelectionMode && currentFragment is HomeFragment) {
            when (item.itemId) {
                R.id.menu_select_all -> {
                    currentFragment.adapter.selectAll()
                    return true
                }

                R.id.menu_clear_selection -> {
                    currentFragment.adapter.clearSelection()
                    return true
                }

                R.id.menu_delete -> {
                    currentFragment.handleDeleteSelected()
                    return true
                }

                android.R.id.home -> {
                    currentFragment.adapter.clearSelection()
                    return true
                }
            }
        }

        return when (item.itemId) {
            R.id.menu_settings -> {
                Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.menu_analyse -> {
                // Handle analyse
                true
            }

            R.id.menu_select_all -> {
                if (currentFragment is HomeFragment) {
                    currentFragment.adapter.selectAll()
                }
                true
            }

            R.id.menu_condition -> {
                showConditionDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun showConditionDialog() {
        val dialog = Dialog(this)
        val condView = layoutInflater.inflate(R.layout.dialog_condition, null)
        val recyclerView = condView.findViewById<RecyclerView>(R.id.RvCondition)

        // FIXME: fetch data from room
        val conditionListAdapter = ConditionListAdapter(
            this,
            emptyList(),
            object : ConditionListAdapter.OnItemClickListener {
                override fun onItemClick(conditionItem: ConditionEntity) {
                    val bundle = bundleOf("ConditionEntity" to conditionItem)
                    navController.navigate(R.id.manageCondition,bundle)
                    dialog.dismiss()
                }
            })

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = conditionListAdapter


        viewModel.getAllConditions().observe(this) { conditions ->
            conditionListAdapter.updateList(conditions)
        }

        dialog.setContentView(condView)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}



