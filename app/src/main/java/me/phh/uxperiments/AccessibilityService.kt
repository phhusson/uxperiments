package me.phh.uxperiments

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

import android.graphics.SurfaceTexture

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import android.hardware.display.DisplayManager

import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.*
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
        for(e in w.availableExtraData) {
            l("${t} - extra $e")
        }

        if(w.error != null) l("${t} - error: ${w.error}")
        if(w.paneTitle != null) l("${t} - pane title: ${w.paneTitle}")
        if(w.rangeInfo != null) l("${t} - range info: ${w.rangeInfo}")
        if(w.tooltipText != null) l("${t} - tooltip: ${w.tooltipText}")
        if(w.viewIdResourceName != null) l("${t} - viewid resource name: ${w.viewIdResourceName}")

        val extras = w.extras ?: Bundle()
        for(key in extras.keySet()) {
            l("${t} - extra bundle $key ${extras.get("key")}")
        }
        if(w.text != null) l("${w.text}")
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
                //TODO: XXX language-dependant
                chatNode
                        .children()
                        .map { it.text ?: it.contentDescription }
                        .filterNotNull()
                        .mapNotNull {
                            //TODO: XXX language-dependant
                            val me = it.contains("Sent")

                            val lines = it.split("\n")
                            val senderDropped = if (isGroup) lines.drop(1) else lines
                            if (senderDropped.size > 0) {
                                val msgLines = senderDropped.take(senderDropped.size - 1)
                                Message(
                                        msg = msgLines.joinToString("\n"),
                                        me = me)
                            } else {
                                null
                            }
                        }

        val p = Person(nick, null)
        val d = Discussion()
        d.persons = setOf(p)
        d.messages = messages
        d.isGroup = isGroup

        val did = DiscussionId(e.packageName.toString(), p)
        if(Discussions.map.contains(did)) {

        }

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

        me = WeakReference(this)
        for(notif in activeNotifications) {
            onNotificationPosted(notif as StatusBarNotification, null)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val pkg = sbn.packageName
        val notification = sbn.notification
        Discussions.handleNotification(pkg, notification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        Discussions.refresh()
    }
}
