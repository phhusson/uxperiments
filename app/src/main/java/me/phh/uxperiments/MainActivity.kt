package me.phh.uxperiments

import android.app.Activity
import android.app.Service
import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this)
        rootLayout.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                .apply { gravity = Gravity.BOTTOM }

        val discussionsContainer = LinearLayout(this)
        discussionsContainer.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.BOTTOM }

        rootLayout.addView(discussionsContainer)
        rootLayout.addView(Space(this))
        for(did in Discussions.map.keys) {
            val p = did.person
            discussionsContainer.addView(
                    TextView(this)
                            .apply {
                                text = "${p.nick} > ${p.uri}"
                            }
            )
        }

        setContentView(rootLayout)
    }

    override fun onResume() {
        super.onResume()

        if(intent.extras != null) {
            Log.d("PHH-Activity", "Resume: Received intent ${intent} ${intent.extras} ${intent.extras.keySet()}")
            for (key in intent.extras.keySet()) {
                Log.d("PHH-Activity", "\t$key")
                val value = intent.extras.get(key)
                Log.d("PHH-Activity", "\t\t=> $value")
            }
        }
    }
}

class MainInteractionService: android.service.voice.VoiceInteractionService() {
    override fun onBind(intent: Intent?): IBinder? {
        Log.d("PHH-Voice", "Bound with intent $intent")
        return super.onBind(intent)
    }

    override fun onReady() {
        super.onReady()
        Log.d("PHH-Voice", "Voice interactor ready")
    }
}

class MainInteractionSessionService: android.service.voice.VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return object : VoiceInteractionSession(this) {
            override fun onHandleAssist(data: Bundle, structure: AssistStructure, content: AssistContent) {
                super.onHandleAssist(data, structure, content)
                Log.d("PHH-Voice", "Received handle assist $data $structure $content")
                Log.d("PHH-Voice","data bundle")
                for(k in data.keySet()) {
                    Log.d("PHH-Voice", "\t$k - ${data[k]}")
                }
            }
            override fun onHandleScreenshot(screenshot: Bitmap?) {
                super.onHandleScreenshot(screenshot)
                Log.d("PHH-Voice", "Received handle screenshot")
            }

            override fun onCreateContentView(): View {
                val rootLayout = LinearLayout(this@MainInteractionSessionService).apply {
                    orientation = LinearLayout.VERTICAL
                }
                rootLayout.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { gravity = Gravity.BOTTOM }
                rootLayout.setBackgroundColor(Color.parseColor("#660000AA"))

                val discussionsContainer = LinearLayout(this@MainInteractionSessionService).apply {
                    orientation = LinearLayout.VERTICAL
                }
                discussionsContainer.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply {
                            gravity = Gravity.BOTTOM
                        }
                discussionsContainer.setBackgroundColor(Color.parseColor("#AA113322"))

                rootLayout.addView(Space(this@MainInteractionSessionService).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 200)
                    setBackgroundColor(Color.parseColor("#AAAA0000"))
                })
                rootLayout.addView(discussionsContainer)
                rootLayout.addView(Space(this@MainInteractionSessionService).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 200)
                    setBackgroundColor(Color.parseColor("#AA00AA00"))
                })
                Log.d("PHH-Voice", "Showing conversations")
                Log.d("PHH-Voice", Discussions.map.keys.toString())
                for(did in Discussions.map.keys) {
                    val p = did.person
                    Log.d("PHH-Voice", "Good morning ${p.nick}")
                    discussionsContainer.addView(
                            TextView(this@MainInteractionSessionService)
                                    .apply {
                                        text = "${p.nick} > ${p.uri}"
                                        setBackgroundColor(Color.parseColor("#FFFFFFFF"))
                                        textSize = 64.0f
                                    }
                    )
                }

                return rootLayout
            }
        }
    }
}