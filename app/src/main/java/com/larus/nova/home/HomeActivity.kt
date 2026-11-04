package com.larus.nova.home

import android.os.Bundle
import androidx.fragment.app.commit
import com.larus.nova.BuildConfig
import com.larus.nova.R
import com.larus.nova.app.AppBarFragmentActivity

class HomeActivity : AppBarFragmentActivity() {
    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "高级工具"
        supportActionBar?.subtitle = BuildConfig.VERSION_NAME
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container, HomeFragment())
            }
        }
    }
}
