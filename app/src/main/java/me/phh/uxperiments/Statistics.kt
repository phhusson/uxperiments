package me.phh.uxperiments

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.list
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp

@Serializable
data class DiscussionStatistics(
        val fOverseen: Double, val fSelected: Double, val fNotified: Double, val fDismissed: Double,
        val lastSeen: Long,
        val seenPersons: Set<Int> //hashes of android.app.Person
)

@Serializable
data class FlattenedStatistics(
        val did: DiscussionId,
        val stats: DiscussionStatistics
)

object Statistics {
    val statistics = mutableMapOf<DiscussionId,DiscussionStatistics>()
    fun load(inputFile: File) {
        val json = Json(JsonConfiguration.Stable)
        val j = inputFile.readText()
        val l = json.parse(FlattenedStatistics.serializer().list, j)
        for(elem in l) {
            statistics[elem.did] = elem.stats
        }
    }

    fun dump(outputFile: File) {
        Discussions.l("Usage statistics:")
        for(did in statistics.keys) {
            update(did)
            Discussions.l("\t$did: ${statistics[did]}")
        }

        val l = statistics.keys.map {
            FlattenedStatistics(it, statistics[it]!!)
        }
        val json = Json(JsonConfiguration.Stable)
        outputFile.writeText(json.stringify(FlattenedStatistics.serializer().list, l))
    }

    fun update(did: DiscussionId,
               selected: Boolean = false,
               overseen: Boolean = false,
               notified: Boolean = false,
               dismissed: Boolean = false) {
        if(!statistics.containsKey(did)) {
            Discussions.l("Reset-ing $did")
            statistics[did] = DiscussionStatistics(
                    0.0, 0.0, 0.0, 0.0,
                    System.currentTimeMillis(), emptySet())
            return
        }

        val s = statistics[did]!!
        val now = System.currentTimeMillis()
        val delta = (now - s.lastSeen).toDouble()
        val period = (24.0*3600.0*1000.0)
        val factor = exp(- delta / period)

        val mO = s.fOverseen * factor + if(overseen) 1.0 else 0.0
        val mS = s.fSelected * factor + if(selected) 1.0 else 0.0
        val mN = s.fNotified * factor + if(notified) 1.0 else 0.0
        val mD = s.fDismissed * factor + if(dismissed) 1.0 else 0.0
        statistics[did] = DiscussionStatistics(mO, mS, mN, mD, System.currentTimeMillis(), emptySet())
    }

    fun onSelected(did: DiscussionId) {
        update(did, selected = true)
    }
    fun onOverseen(did: DiscussionId) {
        update(did, overseen = true)
    }
    fun onNotified(did: DiscussionId) {
        update(did, notified = true)
    }
    fun onDismissed(did: DiscussionId) {
        update(did, dismissed = true)
    }
}