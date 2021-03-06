package me.phh.uxperiments

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import kotlinx.serialization.Serializable
import java.io.File
import java.lang.ref.WeakReference

fun l(s: String) {
    android.util.Log.d("PHH-UX", s)
}

fun l(s: String, t: Throwable) {
    android.util.Log.d("PHH-UX", s, t)
}

@Serializable
data class Person(val nick: String, val uri: String?)
@Serializable
data class Message(val msg: String, val me: Boolean)

class Discussion {
    var isGroup = false
    var persons = setOf<Person>()
    var messages = listOf<Message>()

    var replyAction: Notification.Action? = null
    var contentIntent: PendingIntent? = null
    var deleteIntent : PendingIntent? = null
    var actions = listOf<Notification.Action>()
}

@Serializable
data class DiscussionId(val pkgName: String, val person: Person) {
    constructor(pkgName: String, d: Discussion) : this(pkgName, d.persons.first())
}

object Discussions {
    interface Listener {
        fun onUpdated(did: DiscussionId?)
    }
    val map = mutableMapOf<DiscussionId, Discussion>()
    val icons = mutableMapOf<Person,Icon>()

    //Returns whether discussion has changed
    fun merge(did: DiscussionId, d: Discussion): Boolean {
        if(d == map[did]) return false
        Log.d("PHH-Threads", "Received discussion ${did} ${d.messages.joinToString("\n") { (if(it.me) "\t> " else "\t< ") + it.msg }}")

        //map[did] = d
        if(map.contains(did)) {
            val modD = map[did]!!
            val persons = modD.persons.toMutableSet()
            for(p in d.persons) {
                persons.add(p)
            }
            modD.persons = persons
            if(d.replyAction != null) modD.replyAction = d.replyAction
            if(d.contentIntent != null) modD.contentIntent = d.contentIntent
            if(d.deleteIntent != null) modD.deleteIntent = d.deleteIntent
            if(d.actions.isNotEmpty()) modD.actions = d.actions
            if(modD.messages.isNotEmpty()) {
                val lastMessage = modD.messages.last().msg

            }
        } else {
            map[did] = d
        }
        for(l in listeners) {
            l.onUpdated(did)
        }
        return true
    }

    val listeners = mutableSetOf<Listener>()
    fun registerListener(l: Listener) {
        listeners.add(l)
    }

    fun unregisterListener(l: Listener) {
        listeners.remove(l)
    }
    fun refresh() {
        for(l in listeners) {
            l.onUpdated(null)
        }
    }

    var ctxt: WeakReference<Context>? = null

    fun l(s: String) {
        Log.d("PHH-UX", s)
    }

    fun l(s: String, t: Throwable) {
        Log.d("PHH-UX", s, t)
    }
    fun dumpPerson(p: android.app.Person) {
        l("Person")
        l("\t- key: ${p.key}")
        l("\t- name: ${p.name}")
        l("\t- uri: ${p.uri}")
        l("\t- bot: ${p.isBot()}")
        l("\t- icon: ${p.icon}")
        l("\t- important: ${p.isImportant()}")
    }

    fun onSimpleMessageNotification(pkgName: String, n: Notification, givenUri: String? = null, givenNick: String? = null) {
        // Direct private message
        val firstPeople =
                ((n.extras?.get("android.people.list")) as? java.util.ArrayList<*>)
                        ?.map { it as android.app.Person }
                        ?.firstOrNull()

        var uri: String? = givenUri
        var nick: String? = givenNick

        if(firstPeople != null) {
            uri = firstPeople.uri
            if(uri == null) uri = firstPeople.key
        }

        val messagesBundleArray = n.extras.get(Notification.EXTRA_MESSAGES) ?: return
        val messagesBundle =
                listOf(*messagesBundleArray as Array<*>).map { it as Bundle }

        if(nick == null) nick = messagesBundle.map { (it.get("sender") as? java.lang.CharSequence).toString()}.firstOrNull()
        val senderPerson = messagesBundle.map { (it.get("sender_person") as? android.app.Person)}.firstOrNull()
        if(senderPerson != null) {
            if(nick == null) nick = senderPerson.name?.toString()
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
        val p = setOf(
                Person(
                        uri = uri,
                        nick = nick.trim()
                ))
        if(senderPerson != null && senderPerson.icon != null) {
            l("Setting icon for ${p.first()} to ${senderPerson.icon}")
            icons[p.first()] = senderPerson.icon!!
        }
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
        d.actions = actions.toList()

        val did = DiscussionId(pkgName, d)
        merge(did, d)
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
        val messagesBundleArray = n.extras.get(Notification.EXTRA_MESSAGES)
        val messagesBundle =
                listOf(*messagesBundleArray as Array<*>).map { it as Bundle }

        var uniqueId = n.extras.get("android.hiddenConversationTitle") as? CharSequence
        if(uniqueId == null) uniqueId = n.extras.get("android.conversationTitle") as? CharSequence
        if(uniqueId == null) return
        if(pkgName == "com.whatsapp") {
            val pos = uniqueId.lastIndexOf("(")
            if(pos != -1) {
                uniqueId = uniqueId.substring(0, pos)
            }
        }

        if(pkgName == "org.telegram.messenger") {
            val wearable = n.extras.get("android.wearable.EXTENSIONS") as? Bundle
            val dismissId = wearable?.get("dismissalId") as? CharSequence
            //tgchat371627062_74795
        }

        val messages = messagesBundle.map {
            Message(
                    (it.get("text") as java.lang.CharSequence).toString(),
                    false
            )
        }
        l("Got $uniqueId : ${messages.joinToString(", ")}")

        val personId = Person(nick = uniqueId.toString().trim(), uri = null)
        val replyAction = getReplyAction(n)
        val senderPersons = messagesBundle.map { (it.get("sender_person") as? android.app.Person)}.filterNotNull()
        val senderPerson = senderPersons.firstOrNull()
        if(senderPerson != null && senderPerson.icon != null) {
            l("Setting icon for ${personId} to ${senderPerson.icon}")
            icons[personId] = senderPerson.icon!!
        }

        val persons =
                senderPersons
                        .toSet()
                        .map {
                            Person(
                                    nick = it.name.toString(),
                                    uri = it.uri
                            )
                        }
                        .toSet()

        val d = Discussion()
        d.isGroup = false
        d.messages = messages
        d.persons = persons
        d.replyAction = replyAction
        d.contentIntent = n.contentIntent
        d.deleteIntent = n.deleteIntent
        if(n.actions != null)
            d.actions = n.actions.toList()

        val did = DiscussionId(pkgName, personId)
        merge(did, d)
    }

    val facebookParseLine = Regex("(.*) : (.*)")
    fun onFacebookGroupNotification(pkgName: String, n: Notification) {
        val textLines = (n.extras.get(Notification.EXTRA_TEXT_LINES) as? Array<*>) ?: return
        var uniqueId = (n.extras.get("android.title.big") as? CharSequence) ?: return

        val parsedLines = textLines
                        .map { it as? CharSequence }
                        .filterNotNull()
                        .map { facebookParseLine.find(it) }
                        .filterNotNull()
        val messages = parsedLines
                .map {
                    Message(
                            msg = it.groupValues[2],
                            me = false
                    )
                }

        val persons = parsedLines
                .map { it.groupValues[1]}
                .toSet()
                .map {
                    Person(
                            nick = it,
                            uri = null
                    )
                }
                .toSet()

        l("Got $uniqueId : ${messages.joinToString(", ")}")

        val replyAction = getReplyAction(n)

        val d = Discussion()
        d.isGroup = false
        d.messages = messages
        d.persons = persons
        d.replyAction = replyAction
        d.contentIntent = n.contentIntent
        d.deleteIntent = n.deleteIntent
        if(n.actions != null)
            d.actions = n.actions.toList()

        val icon = n.extras.get("android.largeIcon") as? Icon
        if(icon != null) {
            icons[Person(nick = uniqueId.toString(), uri = null)] = icon
        }
        val did = DiscussionId(pkgName, Person(nick = uniqueId.toString().trim(), uri = null))
        merge(did, d)
    }


    fun onSmsNotification(n: Notification) {
        val phoneNumber = n.extras.get("android.title") as? CharSequence ?: return
        val messages = n.extras.get("android.bigText") as? CharSequence ?: return
        val replyAction = getReplyAction(n)

        val d = Discussion()
        d.isGroup = false
        d.messages = messages.split("\n").toList().map { Message(it, false) }
        d.persons = emptySet()
        d.replyAction = replyAction

        val did = DiscussionId("com.android.messaging", Person(nick = phoneNumber.toString().trim(), uri = null))
        d.contentIntent = n.contentIntent
        d.deleteIntent = n.deleteIntent
        if(n.actions != null)
            d.actions = n.actions.toList()
        merge(did, d)
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

    fun onGmailNotification(pkgName: String, n: Notification) {
        val threadTitle = n.extras?.get("android.text") as? CharSequence ?: return
        val author = n.extras?.get("android.title") as? CharSequence ?: return
        val authorAddress = (n.extras
                ?.get("android.people.list")
                as? List<android.app.Person>)
                ?.firstOrNull()
                ?.uri
                ?.replace("mailto:","")
        val fullMail = n.extras?.get("android.bigText") as? CharSequence ?: return
        val mail =
                if(fullMail.startsWith(threadTitle)) {
                    fullMail.substring(threadTitle.length+1)
                } else {
                    fullMail
                }


        val formatedMail = "$author <$authorAddress>\n\n$mail"

        val d = Discussion()
        d.isGroup = false
        d.messages = listOf(Message(formatedMail, false))
        d.persons = emptySet()

        val did = DiscussionId(pkgName, Person(nick = threadTitle.toString().trim(), uri = null))
        d.contentIntent = n.contentIntent
        d.deleteIntent = n.deleteIntent
        if(n.actions != null)
            d.actions = n.actions.toList()
        merge(did, d)
    }

    fun onMediaNotification(pkgName: String, n: Notification) {
        val mediaToken = n.extras?.get("android.mediaSession") as? android.media.session.MediaSession.Token ?: return
        val c = ctxt?.get() ?: return
        val mediaController = MediaController(c, mediaToken)
        val metadata = mediaController.metadata
        l("Got media metadata $metadata ${metadata?.description}")
        for(key in metadata.keySet()) {
            l("\t - $key")
        }
        l("\textras")
        val e = mediaController.extras?: Bundle()
        for(key in e.keySet()) {
            l("\t- ${key} => ${e.get(key)}")
        }
        mediaController.registerCallback(object: MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata) {
                refresh()
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                l("Got new $title $artist")
            }
        })
    }

    fun handleNotification(pkgName: String, n: Notification) {
        if(n.extras?.get("android.mediaSession") != null) onMediaNotification(pkgName, n)
        when(pkgName) {
            "com.whatsapp", "org.telegram.messenger",
                "com.iskrembilen.quasseldroid", "com.google.android.apps.messaging" -> onGenericNotification(pkgName, n, supportGroups =  true)
            "com.android.messaging" -> onSmsNotification(n)
            "com.google.android.gm" -> onGmailNotification(pkgName, n)
            "com.facebook.mlite" -> onFacebookGroupNotification(pkgName, n)
        }

        l("Got notification $n from $pkgName ${n.smallIcon} ${n.getLargeIcon()}")
        l("\t public version = ${n.publicVersion?.tickerText}, ${n.publicVersion?.extras?.get("android.title")}, ${n.publicVersion?.extras?.get("android.text")}")
        l("\tbigContentView ${n.bigContentView}, contentView ${n.contentView}, headsUpContentView ${n.headsUpContentView}")
        l("\tticker '${n.tickerText}'")
        l("\tcontentIntent ${n.contentIntent} deleteIntent ${n.deleteIntent} fullScreenIntent ${n.fullScreenIntent}")
        val actions = n.actions ?: emptyArray()
        l("\tActions:")
        for(action in actions) {
            l("\t\taction = ${action.semanticAction} ${action.actionIntent} ${action.remoteInputs} ${action.title} ${action.icon}")
            val inputs = action.remoteInputs ?: emptyArray()
            for(input in inputs) {
                l("\t\t\t${input}")
            }
            l("\t\t\t -- extras")
            val extras = action.extras ?: Bundle()
            for(key in extras.keySet()) {
                l("\t\t\t- ${key} => ${extras.get(key)}")
            }
        }
        l("\tExtras:")
        val extras = n.extras ?: Bundle()
        for(key in extras.keySet()) {
            val v = extras.get(key)
            l("\t\t- ${key} => $v")
            if(v is Array<*>) {
                for(i in 0 until v.size) {
                    l("\t\t\t[$i] ${v[i]}")
                }
            }
            if(v is Bundle) {
                for(k in v.keySet()) {
                    val v2 = v[k]
                    l("\t\t\t$k $v2}")
                    if(v2 is Bundle) {
                        for(k2 in v2.keySet()) {
                            l("\t\t\t\t$k2 ${v2[k2]}")
                        }
                    }
                }
            }
            if(v is android.app.Person) {
                dumpPerson(v)
            }
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
            for(message in messages as Array<*>) {
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
    }
}

class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        l("Received broadcast $intent")

        val dumpFile = File(context.getExternalFilesDir("dump")!!, "dump.json")

        val a = intent.action
        when(a) {
            "me.phh.uxperiments.DumpStatistics" -> Statistics.dump(dumpFile)
            "me.phh.uxperiments.LoadStatistics" -> Statistics.load(dumpFile)
        }
    }
}
