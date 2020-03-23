package me.phh.uxperiments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class MainActivity : Activity() {
    lateinit var rootLayout: Bar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootLayout = Bar(this)

        setContentView(rootLayout)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("PHH-Activity", "New Intent intent ${intent} ${intent.extras} ${intent.extras.keySet()}")
        for(key in intent.extras.keySet()) {
            Log.d("PHH-Activity", "\t$key")
            val value = intent.extras.get(key)
            Log.d("PHH-Activity", "\t\t=> $value")
        }
    }

    override fun onResume() {
        super.onResume()
        rootLayout.updateViews()

        Log.d("PHH-Activity", "Resume: Received intent ${intent} ${intent.extras} ${intent.extras.keySet()}")
        for(key in intent.extras.keySet()) {
            Log.d("PHH-Activity", "\t$key")
            val value = intent.extras.get(key)
            Log.d("PHH-Activity", "\t\t=> $value")
        }
    }
}
