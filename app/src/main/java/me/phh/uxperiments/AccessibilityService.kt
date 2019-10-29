package me.phh.uxperiments

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

import android.graphics.SurfaceTexture

import android.os.Parcelable

import android.app.ActivityOptions
import android.app.Notification
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import android.hardware.display.DisplayManager

import android.hardware.display.VirtualDisplay
import android.view.Gravity

import android.view.Surface
import android.view.WindowManager

data class Person(val nick: String, val uri: String?)
data class Message(val msg: String, val me: Boolean)

class Discussion {
    var isGroup = false
    var persons = listOf<Person>()
    var messages = listOf<Message>()

    var replyAction: Notification.Action? = null
    var contentIntent: PendingIntent? = null
    var deleteIntent : PendingIntent? = null
}

data class DiscussionId(val pkgName: String, val person: Person) {
    constructor(pkgName: String, d: Discussion) : this(pkgName, d.persons[0])
}
object Discussions {
    interface Listener {
        fun onUpdated(did: DiscussionId)
    }
    val map = mutableMapOf<DiscussionId, Discussion>()
    fun merge(did: DiscussionId, d: Discussion) {
        map[did] = d
        for(l in listeners) {
            l.onUpdated(did)
        }
    }

    val listeners = mutableSetOf<Listener>()
    fun registerListener(l: Listener) {
        listeners.add(l)
    }

    fun unregisterListener(l: Listener) {
        listeners.remove(l)
    }
}

class Accessibility : AccessibilityService() {
    companion object {
        const val PKG_GMAIL = "com.google.android.gm"
        const val PKG_WHATSAPP = "com.whatsapp"

        fun l(s: String) {
            android.util.Log.d("PHH-UX", s)
        }

        fun l(s: String, t: Throwable) {
            android.util.Log.d("PHH-UX", s, t)
        }
        fun dumpPerson(p: android.app.Person) {
            l("Person")
            l("\t- key: ${p.key}")
            l("\t- name: ${p.name}")
            l("\t- uri: ${p.uri}")
            l("\t- bot: ${p.isBot()}")
            l("\t- important: ${p.isImportant()}")
        }

        fun onSimpleMessageNotification(pkgName: String, n: Notification, givenUri: String? = null, givenNick: String? = null) {
            // Direct private message
            val peopleList = n.extras.get("android.people.list")
            var uri: String? = givenUri
            var nick: String? = givenNick

            if(peopleList != null) {
                uri = (peopleList as java.util.ArrayList<android.app.Person>).firstOrNull()?.uri
                if(uri == null) uri = peopleList.firstOrNull()?.key
            }

            val messagesBundleArray = n.extras.get(Notification.EXTRA_MESSAGES) ?: return
            val messagesBundle =
                    java.util.Arrays.asList(*messagesBundleArray as Array<Parcelable>).map { it as Bundle }

            if(nick == null) nick = messagesBundle.map { (it.get("sender") as? java.lang.CharSequence).toString()}.firstOrNull()
            val senderPerson = messagesBundle.map { (it.get("sender_person") as? android.app.Person)}.firstOrNull()
            if(senderPerson != null) {
                if(nick == null) nick = senderPerson.name.toString()
                if(uri == null) uri = senderPerson.uri
                if(uri == null) uri = senderPerson.key
            }

            val messages = messagesBundle.map {
                Message(
                        (it.get("text") as java.lang.CharSequence).toString(),
                        false
                )
            }
            l("Got $uri/$nick : ${messages.joinToString(", ")}")

            if(nick == null) return
            val p = listOf(
                    Person(
                            uri = uri,
                            nick = nick
                    ))
            val actions = n.actions ?: emptyArray()
            var replyAction = actions.find { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY}
            if(replyAction == null) {
                replyAction = actions.find { it.remoteInputs != null && it.remoteInputs.isNotEmpty() }
            }


            val d = Discussion()
            d.isGroup = false
            d.messages = messages
            d.persons = p
            d.replyAction = replyAction
            d.contentIntent = n.contentIntent
            d.deleteIntent = n.deleteIntent

            val did = DiscussionId(pkgName, d)
            Discussions.merge(did, d)
        }

        fun getReplyAction(n: Notification): Notification.Action? {
            val extras = n.extras ?: Bundle()
            val actions = n.actions
            var replyAction = actions?.find { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
            if(replyAction == null) {
                replyAction = actions?.find { it.remoteInputs != null && it.remoteInputs.isNotEmpty() }
            }

            val wearable = extras.get("android.wearable.EXTENSIONS") as? Bundle
            val wearableActions = wearable?.get("actions") as? java.util.ArrayList<Notification.Action>
            if(replyAction == null && wearableActions != null) {
                replyAction = wearableActions.find { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
                if(replyAction == null) {
                    replyAction = wearableActions.find { it.remoteInputs != null && it.remoteInputs.isNotEmpty() }
                }
            }

            return replyAction
        }

        fun onSimpleGroupNotification(pkgName: String, n: Notification) {
            val messagesBundleArray = n.extras.get(Notification.EXTRA_MESSAGES) ?: return
            val messagesBundle =
                    java.util.Arrays.asList(*messagesBundleArray as Array<Parcelable>).map { it as Bundle }

            var uniqueId = n.extras.get("android.hiddenConversationTitle") as? CharSequence
            if(uniqueId == null) uniqueId = n.extras.get("android.conversationTitle") as? CharSequence
            if(uniqueId == null) return
            if(pkgName == "com.whatsapp") {
                val pos = uniqueId.lastIndexOf("(")
                if(pos != -1) {
                    uniqueId = uniqueId.substring(0, pos)
                }
            }

            val messages = messagesBundle.map {
                Message(
                        (it.get("text") as java.lang.CharSequence).toString(),
                        false
                )
            }
            l("Got $uniqueId : ${messages.joinToString(", ")}")



            val replyAction = getReplyAction(n)

            val d = Discussion()
            d.isGroup = false
            d.messages = messages
            d.persons = emptyList()
            d.replyAction = replyAction
            d.contentIntent = n.contentIntent
            d.deleteIntent = n.deleteIntent

            val did = DiscussionId(pkgName, Person(nick = uniqueId.toString(), uri = null))
            Discussions.merge(did, d)
        }

        fun onSmsNotification(n: Notification) {
            val phoneNumber = n.extras.get("android.title") as? CharSequence ?: return
            val messages = n.extras.get("android.bigText") as? CharSequence ?: return
            val replyAction = getReplyAction(n)


            val d = Discussion()
            d.isGroup = false
            d.messages = messages.split("\n").toList().map { Message(it, false) }
            d.persons = emptyList()
            d.replyAction = replyAction

            val did = DiscussionId("com.android.messaging", Person(nick = phoneNumber.toString(), uri = null))
            d.contentIntent = n.contentIntent
            d.deleteIntent = n.deleteIntent
            Discussions.merge(did, d)
        }

        fun onGenericNotification(pkgName: String, n: Notification, supportGroups: Boolean = false) {
            l("Generic notification")
            val extras = n.extras ?: Bundle()
            val isGroup = extras?.get("android.isGroupConversation") as? Boolean ?: false
            l("is group ${isGroup}")
            if(isGroup) {
                if(supportGroups)
                    onSimpleGroupNotification(pkgName, n)
            } else {
                onSimpleMessageNotification(pkgName, n)
            }
        }

        fun handleNotification(pkgName: String, n: Notification) {
            /*if(pkgName == "org.telegram.messenger") onTelegramNotification(n)
            if(pkgName == "com.iskrembilen.quasseldroid") onQuasselNotification(n)
            if(pkgName == "com.whatsapp") onGenericNotification(pkgName, n)*/
            if(pkgName == "com.android.messaging") onSmsNotification(n)
            when(pkgName) {
                "com.whatsapp", "org.telegram.messenger", "com.iskrembilen.quasseldroid" -> onGenericNotification(pkgName, n, supportGroups =  true)
            }

            l("Got notification $n from $pkgName")
            l("\t public version = ${n.publicVersion?.tickerText}, ${n.publicVersion?.extras?.get("android.title")}, ${n.publicVersion?.extras?.get("android.text")}")
            l("\tbigContentView ${n.bigContentView}, contentView ${n.contentView}, headsUpContentView ${n.headsUpContentView}")
            l("\tticker '${n.tickerText}'")
            l("\tcontentIntent ${n.contentIntent} deleteIntent ${n.deleteIntent} fullScreenIntent ${n.fullScreenIntent}")
            val actions = n.actions ?: emptyArray()
            l("\tActions:")
            for(action in actions) {
                l("\t\taction = ${action.semanticAction} ${action.actionIntent} ${action.remoteInputs}")
                val inputs = action.remoteInputs ?: emptyArray()
                for(input in inputs) {
                    l("\t\t\t${input}")
                }
            }
            l("\tExtras:")
            val extras = n.extras ?: Bundle()
            for(key in extras.keySet()) {
                l("\t\t- ${key} => ${extras.get(key)}")
            }

            val peoples = extras?.get("android.people.list")
            if(peoples != null) {
                l("people type is ${peoples.javaClass}")
                if(peoples is List<*>) {
                    for(people in peoples) {
                        if(people is android.app.Person) {
                            dumpPerson(people)
                        }
                    }
                }
            }

            val wearable = extras?.get("android.wearable.EXTENSIONS")
            if(wearable != null) {
                for(key in (wearable as Bundle).keySet()) {
                    l("Wearable - $key ${wearable.get(key)}")
                }
                val actions = wearable?.get("actions") as? java.util.ArrayList<Notification.Action> ?: emptyList<Notification.Action>()
                for(action in actions) {
                    l("\t\taction = ${action.semanticAction} ${action.actionIntent} ${action.remoteInputs}")
                    val inputs = action.remoteInputs ?: emptyArray()
                    for(input in inputs) {
                        l("\t\t\t${input}")
                    }
                }
            }

            val car = extras?.get("android.car.EXTENSIONS")
            if(car != null) {
                for(key in (car as Bundle).keySet()) {
                    l("Car - $key ${car.get(key)}")
                }
                val car_conversation = car.get("car_conversation")
                if(car_conversation != null) {
                    for(key in (car_conversation as Bundle).keySet()) {
                        l("\tcar conversation - $key ${car_conversation.get(key)}")
                    }
                    val participants = car_conversation.get("participants") as? Array<String> ?: emptyArray()
                    for(p in participants) {
                        l("\t\tparticipant $p")
                    }
                    val messages = car_conversation.get("messages") as? Array<Parcelable> ?: emptyArray()
                    for(m in messages) {
                        l("\t\tmessage $m")
                        for(key in (m as Bundle).keySet()) {
                            l("\t\t\t- $key ${m.get(key)}")
                        }
                    }
                }
                val invisibleActions = car?.get("invisible_actions")
                if(invisibleActions != null) {
                    for(key in (invisibleActions as Bundle).keySet()) {
                        l("\tinvisible actions - $key ${invisibleActions.get(key)}")
                    }
                }
            }
            val messages = extras?.get("android.messages")
            if(messages != null) {
                for(message in messages as Array<Parcelable>) {
                    l("Got message $message")
                    if(message is Bundle) {
                        for(key in message.keySet()) {
                            l("\t$key ${message.get(key)}")
                        }
                        val senderPerson = message.get("sender_person")
                        if(senderPerson is android.app.Person) {
                            dumpPerson(senderPerson)
                        }
                    }
                }
            }

            val person = extras?.get("android.messagingUser")
            l("Got person $person")
            if(person is android.app.Person) {
                dumpPerson(person)
            }

            val textLines = extras?.get("android.textLines")
            if(textLines != null) {
                l("Got lines")
                for(line in textLines as Array<java.lang.CharSequence>) {
                    l("\t${line}")
                }
            }
        }

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

    override fun onServiceConnected() {
        l("Good morning")
    }

    fun sendPhhAnswer(pkg: String, notification: Notification) {
        val actions = notification.actions ?: emptyArray()
        l("hihi")
        val whatsapp = notification.tickerText?.startsWith("Message de Ph") ?: false
        val hangout = notification.tickerText?.startsWith("Pierre-Hugues") ?: false
        val telegram = notification.extras.get("android.title").equals("Phh")

        if(pkg == PKG_GMAIL) {
            if(true) {
                l("haha")
                val i = notification.contentIntent
                val person = notification.extras.get("android.subText")
                handler.postDelayed(Runnable {
                    l("Starting inside my own display!")
                    val options = ActivityOptions.makeBasic()
                    //options.setLaunchDisplayId(MainActivity.sVirtualDisplay!!.getDisplay().getDisplayId())
                    options.setLaunchDisplayId(getDisplay(pkg + person.hashCode()).getDisplay().getDisplayId())
                    i.send(this, 0, null, null, null, null, options.toBundle())
                }, 1000L)
                return
            }

        }
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

        Discussions.merge(
                DiscussionId(e.packageName.toString(), d),
                d
                )
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
            handleNotification(e.packageName.toString(), notification)
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
        Accessibility.handleNotification(pkg, notification)
    }
}
