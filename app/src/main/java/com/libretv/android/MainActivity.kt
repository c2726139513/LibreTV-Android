package com.libretv.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.libretv.android.ui.browse.BrowseFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_frame, BrowseFragment())
                .commit()
        }
    }
}
