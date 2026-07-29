package com.vidhub.android.ui.detail

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.vidhub.android.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.details_container, DetailFragment())
                .commit()
        }
    }
}
