package me.phh.uxperiments

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

import android.graphics.SurfaceTexture

import android.app.ActivityOptions
import android.app.Notification
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import android.hardware.display.DisplayManager

import android.hardware.display.VirtualDisplay
import android.view.Gravity

import android.view.Surface
import android.view.WindowManager
import java.lang.ref.WeakReference


class Accessibility : AccessibilityService() {
    companion object {
        var ctxt: WeakReference<Context>? = null
    }
    private val handlerThread = HandlerThread("Accessibility service").also { it.start() }
    val handler = Handler(handlerThread.looper)

    var pkgToDisplay = mutableMapOf<String, VirtualDisplay>()
    private fun getDisplay(pkg: String): VirtualDisplay {
        if(pkgToDisplay.contains(pkg)) return pkgToDisplay[pkg]!!
        val dm = getSystemService(DisplayManager::class.java)
        val virtualDisplay = dm.createVirtualDisplay("phh-ux-$pkg", 360, 640, 120,
            Surface(SurfaceTexture(pkg.hashCode())),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC)
        pkgToDisplay[pkg] = virtualDisplay
        return virtualDisplay
    }

    fun sendPhhAnswer(pkg: String, notification: Notification) {
        l("haha")
        val i = notification.contentIntent
        val person = notification.extras.get("android.subText")
        handler.postDelayed(Runnable {
            l("Starting inside my own display!")
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = getDisplay(pkg + person.hashCode()).getDisplay().getDisplayId()
            i.send(this, 0, null, null, null, null, options.toBundle())
        }, 1000L)
    }

    override fun onServiceConnected() {
        l("Good morning")
    }

    fun browse(w: AccessibilityNodeInfo, n: Int = 0) {
        val t = (0 until n).map {'\t'}.joinToString("")
        val bounds = Rect()
        w.getBoundsInScreen(bounds)
        l("${t}${w.className}; ${w.contentDescription} (${w.childCount}), ${w.viewIdResourceName}, ${w.windowId} ${bounds.top}x${bounds.left}-${bounds.bottom}x${bounds.right}:")
        if(w.text != null) l("${w.text}")
        val extras = w.extras ?: Bundle()
        for(key in extras.keySet()) {
            l("${t} - $key ${extras.get("key")}")
        }
        for(i in 0 until w.childCount) {
            val child = w.getChild(i)
            if(child != null)
                browse(child, n+1)
        }
    }

    fun AccessibilityNodeInfo.children(): List<AccessibilityNodeInfo> {
        return (0 until childCount).map { this.getChild(it) }
    }

    fun onTelegramAccessibility(e: AccessibilityEvent) {
        if(e.packageName != "org.telegram.messenger") return
        if(e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        if(e.className != "android.widget.FrameLayout") return
        val root = e.source

        val isGroup = {
            val node = root
                    ?.children()
                    ?.firstOrNull() { it.className.endsWith("FrameLayout")}
                    ?.children()
                    ?.firstOrNull() { it.className.endsWith("FrameLayout") }
                    ?.children()
                    ?.getOrNull(1)
                    ?.text ?: ""
            //TODO: XXX language-dependant
            node.contains("members")
        }()
        if(isGroup) return

        // Look for a child of type RecyclerView with exclusively ViewGroup in it
        val chatNodes =
                root
                        .children()
                        .filter { it.className.endsWith("RecyclerView")}
                        .filter { it.children().firstOrNull { !it.className.endsWith("ViewGroup") } == null }
        if(chatNodes.size != 1) {
            l("Failed finding Telegram chat node...")
            return
        }
        val chatNode = chatNodes.first()

        val nick =
                root
                        .children()
                        .firstOrNull { it.className.endsWith("FrameLayout")}
                        ?.children()
                        ?.firstOrNull { it.className.endsWith("FrameLayout")}
                        ?.children()
                        ?.firstOrNull()
                        ?.text
                        ?.toString() ?: ""

        val messages =
                chatNode
                        .children()
                        .map { it.text ?: return }
                        .map {
                            //TODO: XXX language-dependant
                            val lines = it.split("\n")
                            val msgLines = lines.take(lines.size-1)

                            Message(
                                    msg = msgLines.joinToString("\n"),
                                    me = it.contains("Sent"))
                        }

        val d = Discussion()
        d.persons = listOf(Person(nick, null))
        d.messages = messages
        d.isGroup = isGroup

        /*Discussions.merge(
                DiscussionId(e.packageName.toString(), d),
                d
                )*/
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        onTelegramAccessibility(e)
        l("Got event ${e.getEventType()}")
        l("\tpackage src = ${e.getPackageName()}")
        l("\tsrc class = ${e.getClassName()}")
        if(e.packageName == "com.android.systemui" || e.packageName == "com.android.launcher3") return
        if(e.source != null) {
            l("\tsrc = ${e.source.getHintText()}")
            browse(e.source)
        }
        if(e.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if(e.parcelableData == null) return
            val notification = e.parcelableData as Notification
            //l("\tContent Intent:")
            //sendPhhAnswer(e.packageName.toString(), notification)
            Discussions.handleNotification(e.packageName.toString(), notification)
        }
    }

    override fun onInterrupt() {
    }
}


class NotificationService : NotificationListenerService() {
    companion object {
        val popupParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            flags =
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

            type = 2024 /* TYPE_NAVIGATION_BAR_PANEL */
            gravity = Gravity.BOTTOM
            title = "phh-ux-overlay"
        }
        var inited = false
        var initing = true
    }

    lateinit var rootLayout : Bar
    lateinit var wm: WindowManager
    fun initBar() {
        l("Adding phh-ux view plane ${inited}")
        if(inited) return

        wm = getSystemService(WindowManager::class.java)!!
        inited = true

        rootLayout = Bar(this, includePopup = false)

        val params = WindowManager.LayoutParams()
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        params.type = 2024 /* TYPE_NAVIGATION_BAR_PANEL */

        params.gravity = Gravity.BOTTOM
        params.title = "phh-ux"

        wm.addView(rootLayout, params)

        popupParams.y = rootLayout.barHeight
        wm.addView(rootLayout.popupContainer, popupParams)
    }

    override fun onListenerConnected() {
        Accessibility.ctxt = WeakReference(this)
        l("onListenerConnected")
        initBar()
        //requestListenerHints(HINT_HOST_DISABLE_NOTIFICATION_EFFECTS)

        // Retrieve notifications that are already displayed
        val nm = getSystemService(NotificationManager::class.java)
        nm.getActiveNotifications() // Useless call, so that NotificationManager fetches sService
        val serviceField = NotificationManager::class.java.getDeclaredField("sService")
        serviceField.isAccessible = true
        val nms = serviceField.get(nm)
        val getActiveNotifications = nms.javaClass.getMethod("getActiveNotifications", String::class.java)

        val notifs = getActiveNotifications.invoke(nms, packageName) as Array<*>
        l("Got notifs $notifs ${notifs.size}")
        for(notif in notifs) {
            onNotificationPosted(notif as StatusBarNotification, null)
        }
        initing = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val pkg = sbn.packageName
        val notification = sbn.notification
        Discussions.handleNotification(pkg, notification)
    }
}
