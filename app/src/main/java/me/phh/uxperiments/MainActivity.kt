package me.phh.uxperiments

import android.app.Activity
import android.os.Bundle

fun l(s: String) {
    android.util.Log.d("PHH-UX", s)
}

fun l(s: String, t: Throwable) {
    android.util.Log.d("PHH-UX", s, t)
}

class MainActivity : Activity() {

    lateinit var rootLayout: Bar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootLayout = Bar(this)

        setContentView(rootLayout)
    }

    override fun onResume() {
        super.onResume()
        rootLayout.updateViews()
    }
}
