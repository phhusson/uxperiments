package me.phh.uxperiments

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.service.voice.VoiceInteractionSession
import android.text.Layout
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import java.lang.Exception
import java.util.ArrayList

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
            val discussionsContainer = LinearLayout(this@MainInteractionSessionService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(Color.parseColor("#AA113322"))
            }

            fun refresh() {
                discussionsContainer.removeAllViews()

                //Now we show all notifications
                discussionsContainer.addView(
                        LinearLayout(this@MainInteractionSessionService).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.0f)
                            val notifs = NotificationService.me!!.get()!!.getActiveNotifications()!!

                            for (n in notifs) {
                                val notification = n.notification
                                if ((notification.flags and (Notification.FLAG_GROUP_SUMMARY/* or Notification.FLAG_ONGOING_EVENT*/)) != 0) continue

                                val notifiationIcon = notification.getLargeIcon()
                                        ?: notification.smallIcon
                                val extraIcon = notification.extras.get("android.largeIcon") as? Icon

                                val peopleIcon =
                                        (notification.extras.get("android.people.list") as? List<*>)
                                                ?.map { it as? android.app.Person }
                                                ?.filterNotNull()
                                                ?.map { it.icon }
                                                ?.filterNotNull()
                                                ?.firstOrNull()


                                val messagingPersonIcon =
                                        (notification.extras.get("android.messages") as? Array<*>)
                                                ?.map { it as? Bundle }
                                                ?.filterNotNull()
                                                ?.map { it.get("sender_person") as? android.app.Person }
                                                ?.filterNotNull()
                                                ?.map { it.icon }
                                                ?.filterNotNull()
                                                ?.firstOrNull()

                                val messagingUser = notification.extras.get("android.messagingUser") as? android.app.Person
                                if (messagingUser != null) Discussions.dumpPerson(messagingUser)

                                val messages =
                                        (notification.extras.get("android.messages") as? Array<*>)
                                                ?.map { it as? Bundle }
                                                ?.filterNotNull()
                                                ?.map { it.get("text") as? CharSequence }
                                                ?.filterNotNull()
                                                ?.joinToString("\n")
                                val bigText = notification.extras.get("android.bigText") as? CharSequence
                                val title = notification.extras.get("android.title") as? CharSequence
                                val text = notification.extras.get("android.text") as? CharSequence
                                val madeText = if (title != null && text != null) "$title\n$text" else null

                                if (messages != null && messages.contains("test")) {
                                    Discussions.handleNotification("bite", notification)
                                }

                                val icon = messagingPersonIcon ?: peopleIcon ?: extraIcon ?: notifiationIcon
                                if (icon != null) addView(
                                        object: LinearLayout(this@MainInteractionSessionService) {
                                            override fun getTouchables(): ArrayList<View> {
                                                return super.getTouchables()
                                            }
                                        }.apply {
                                            val gestureDetector = GestureDetector(this@MainInteractionSessionService, object: GestureDetector.SimpleOnGestureListener() {
                                                override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                                                    l("Swing $velocityX $velocityY")
                                                    //Horizontal swing
                                                    if(velocityY == 0.0f || velocityX / velocityY > 3) {
                                                        if(velocityX > 3000) {
                                                            l("Canceling notification")
                                                            NotificationService.me!!.get()!!.cancelNotification(n.key)
                                                            return true
                                                        }
                                                    }
                                                    return false
                                                }
                                            })
                                            gestureDetector.setOnDoubleTapListener(object: GestureDetector.OnDoubleTapListener {
                                                override fun onDoubleTap(e: MotionEvent?): Boolean {
                                                    l("received double tap")
                                                    NotificationService.me!!.get()!!.cancelNotification(n.key)
                                                    hide()
                                                    notification.contentIntent.send()
                                                    return true
                                                }

                                                override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
                                                    l("received double tap event")
                                                    return true
                                                }

                                                override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                                                    l("received single tap confirmed")
                                                    return true
                                                }
                                            })

                                            setOnTouchListener {
                                                _, event ->
                                                l("Received touch event $event")
                                                val r = gestureDetector.onTouchEvent(event)
                                                l("\t returned $r")
                                                true
                                            }

                                            setBackgroundColor(Color.BLACK)
                                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 192).apply {
                                                gravity = Gravity.LEFT
                                            }
                                            addView(ImageView(this@MainInteractionSessionService).apply {
                                                layoutParams = ViewGroup.LayoutParams(192, LinearLayout.LayoutParams.MATCH_PARENT).also {
                                                    gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
                                                }
                                                setImageIcon(icon)
                                            })
                                            addView(TextView(this@MainInteractionSessionService).apply {
                                                textSize = 20.0f
                                                setText(messages ?: bigText ?: madeText ?: title
                                                ?: notification.tickerText)
                                                setTextColor(Color.parseColor("#FFDDDDDD"))
                                            })
                                        })
                            }
                        })

                // This is the column listing all current discussions
                discussionsContainer.addView(
                        LinearLayout(this@MainInteractionSessionService).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(192, LinearLayout.LayoutParams.MATCH_PARENT).also {
                                gravity = Gravity.TOP
                            }
                            gravity = Gravity.TOP


                            for( (did, d) in Discussions.map) {
                                //Can't handle groups because of no icons
                                val personIcon =
                                        d.persons
                                                .map { Discussions.icons[it] }
                                                .filterNotNull()
                                                .firstOrNull()

                                val groupIcon = Discussions.icons[did.person]

                                val icon = personIcon ?: groupIcon ?: continue

                                addView(
                                        ImageView(this@MainInteractionSessionService).apply {
                                            layoutParams = ViewGroup.LayoutParams(192, 192).also {
                                                gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
                                            }
                                            setImageIcon(icon)
                                            setBackgroundColor(Color.parseColor("#aaf81894"))
                                        }
                                )
                            }
                            addView(View(this@MainInteractionSessionService).apply {
                                setBackgroundColor(Color.TRANSPARENT)
                            })
                        }
                )
            }

            init {
                Discussions.registerListener(object: Discussions.Listener {
                    override fun onUpdated(did: DiscussionId?) {
                        refresh()
                    }
                })
            }
            override fun onHandleAssist(data: Bundle, structure: AssistStructure, content: AssistContent) {
                super.onHandleAssist(data, structure, content)
                Log.d("PHH-Voice", "Received handle assist $data $structure $content")
                Log.d("PHH-Voice", "Intent: ${content.intent}")
                if(content.intent.extras != null) {
                    for(k in content.intent.extras.keySet()) {
                        Log.d("PHH-Voice", "\t$k - ${content.extras[k]}")
                    }
                }
                Log.d("PHH-Voice", "Clip data: ${content.clipData?.description}")
                Log.d("PHH-Voice", "Web URI: ${content.webUri}")
                if(content.extras != null) {
                    for(k in content.extras.keySet()) {
                        Log.d("PHH-Voice", "\t$k - ${content.extras[k]}")
                    }
                }
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



                rootLayout.addView(Space(this@MainInteractionSessionService).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400)
                    setBackgroundColor(Color.parseColor("#AAAA0000"))
                })
                rootLayout.addView(discussionsContainer)
                rootLayout.addView(Space(this@MainInteractionSessionService).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 200)
                    setBackgroundColor(Color.parseColor("#AA00AA00"))
                })

                refresh()

                return rootLayout
            }
        }
    }
}