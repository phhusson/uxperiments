package me.phh.uxperiments

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

class DiscussionOverlay(discussion: Discussion, val did: DiscussionId, context: Context) : View(context), SelectorView {
    private val paint = Paint()

    private val overlayText: TextView
    var currentDiscussion: Discussion = discussion
    override fun updateDiscussion(d: Discussion) {
        currentDiscussion = d
        l("Updating text")
        overlayText.text = did.person.nick + "\n" + currentDiscussion.messages.map { it.msg }.joinToString("\n")
    }

    init {
        isClickable = true
    }
    private val overlayView = LinearLayout(context).also {
        it.orientation = LinearLayout.VERTICAL
        it.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        )
        overlayText = TextView(context).also {
            it.text = did.person.nick + "\n" + currentDiscussion.messages.map { it.msg }.joinToString("\n")
            it.setTextColor(Color.GRAY)
            it.textSize = 20.0f
        }
        it.addView(overlayText)
        if(currentDiscussion.replyAction != null) {
            it.addView(
                    EditText(context).also {
                        it.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        it.setSingleLine()
                        it.imeOptions = EditorInfo.IME_ACTION_DONE
                        it.setOnEditorActionListener(object : TextView.OnEditorActionListener {
                            override fun onEditorAction(p0: TextView, actionId: Int, p2: KeyEvent?): Boolean {
                                if (actionId == EditorInfo.IME_ACTION_DONE) {
                                    val msg = p0.text.toString()
                                    if(msg == "") return true

                                    l("Answering to discussion ${did} : ${currentDiscussion}")
                                    val a = currentDiscussion.replyAction ?: return true
                                    val remoteInput = a.remoteInputs?.firstOrNull()
                                            ?: return true

                                    val b = Bundle()

                                    b.putString(remoteInput.resultKey, msg)
                                    val i = Intent()
                                    RemoteInput.addResultsToIntent(a.remoteInputs, i, b)
                                    RemoteInput.setResultsSource(i, RemoteInput.SOURCE_FREE_FORM_INPUT)
                                    Accessibility.l("Sending!!!")
                                    a.actionIntent.send(context, 0, i)

                                    p0.text = ""

                                    val newDiscussion = Discussion()
                                    newDiscussion.replyAction = currentDiscussion.replyAction
                                    newDiscussion.persons = currentDiscussion.persons
                                    newDiscussion.isGroup = currentDiscussion.isGroup
                                    newDiscussion.messages = currentDiscussion.messages + listOf(Message("\t\t$msg", true))

                                    Discussions.merge(did, newDiscussion)

                                    return true
                                }
                                return false
                            }
                        })
                    }
            )
        }
    }

    override fun getOverlayView(): View {
        return overlayView
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val i = did.hashCode() % 3
        l("Got ${did.hashCode()}")

        paint.color = when(i) {
            0 -> Color.BLUE
            1 -> Color.RED
            2 -> Color.YELLOW
            3 -> Color.GREEN
            else -> Color.GRAY
        }
        val radius = min(width, height) /2.5f
        canvas.drawCircle(width/2.0f, height/2.0f, radius, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(heightSize*1.2.toInt(), heightSize)
    }
}