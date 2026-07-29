package com.vidhub.android.ui.search

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.vidhub.android.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.search_container, SearchFragment())
                .commit()
        }
    }
}
