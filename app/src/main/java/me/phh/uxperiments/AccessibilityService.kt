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

import java.io.File
import java.util.*

class Accessibility : AccessibilityService() {
    companion object {
        var ctxt: WeakReference<Context>? = null
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
        return (0 until childCount).map { this.getChild(it) }.filterNotNull()
    }

    fun onTelegramAccessibility(e: AccessibilityEvent) {
        if(e.packageName != "org.telegram.messenger") return
        if(e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        if(e.className != "android.widget.FrameLayout") return
        val root = e.source ?: return

        val isGroup = {
            val node = root
                    .children()
                    .firstOrNull() { it.className.endsWith("FrameLayout")}
                    ?.children()
                    ?.firstOrNull() { it.className.endsWith("FrameLayout") }
                    ?.children()
                    ?.filter { it.className.endsWith("TextView")}
                    ?.getOrNull(1)
                    ?.text ?: ""

            //TODO: XXX language-dependant, and won't work if someone is typing
            node.contains("members")
        }()

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
                        ?.filter { it.className.endsWith("TextView")}
                        ?.firstOrNull()
                        ?.text
                        ?.toString()
        if(nick == null) {
            l("Failed getting nick")
            return
        }

        val messages =
                chatNode
                        .children()
                        .map { it.text ?: it.contentDescription }
                        .filterNotNull()
                        .map {
                            //TODO: XXX language-dependant
                            val me = it.contains("Sent")

                            val lines = it.split("\n")
                            val msgLines = lines.take(lines.size-1)

                            Message(
                                    msg = msgLines.joinToString("\n"),
                                    me = me)
                        }

        val d = Discussion()
        d.persons = listOf(Person(nick, null))
        d.messages = messages
        d.isGroup = isGroup

        Discussions.merge(
                DiscussionId(e.packageName.toString(), d),
                d)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if(e.packageName == "com.android.systemui" || e.packageName == "com.android.launcher3") return

        onTelegramAccessibility(e)
        l("Got event ${e.getEventType()}")
        l("\tpackage src = ${e.getPackageName()}")
        l("\tsrc class = ${e.getClassName()}")
        if(e.source != null) {
            l("\tsrc = ${e.source.getHintText()}")
            browse(e.source)
        }
        if(e.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            /*
            if(e.parcelableData == null) return
            val notification = e.parcelableData as Notification
            //l("\tContent Intent:")
            //sendPhhAnswer(e.packageName.toString(), notification)
            Discussions.handleNotification(e.packageName.toString(), notification)
            */
        }
    }

    override fun onInterrupt() {
    }
}


class NotificationService : NotificationListenerService() {
    companion object {
        var me: WeakReference<NotificationService>? = null
    }
    override fun onCreate() {
        super.onCreate()
        try {
            val dumpFile = File(getExternalFilesDir("dump")!!, "dump.json")
            Statistics.load(dumpFile)
        } catch(t: Throwable) {}
    }

    override fun onListenerConnected() {
        Accessibility.ctxt = WeakReference(this)
        l("onListenerConnected")
        //requestListenerHints(HINT_HOST_DISABLE_NOTIFICATION_EFFECTS)

        me = WeakReference(this)
        val notifs = getActiveNotifications()

        l("Got notifs $notifs ${notifs.size}")
        for(notif in notifs) {
            onNotificationPosted(notif as StatusBarNotification, null)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val pkg = sbn.packageName
        val notification = sbn.notification
        //Discussions.handleNotification(pkg, notification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
    }
}
