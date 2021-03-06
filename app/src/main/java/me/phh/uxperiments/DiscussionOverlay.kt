package me.phh.uxperiments

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.animation.Transformation
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

class DiscussionOverlay(discussion: Discussion, val did: DiscussionId, context: Context) : View(context), SelectorView {
    val clearOnOverview = Runnable {
        currentDiscussion.deleteIntent?.send()
        animate().setDuration(2000).alpha(.3f)
    }
    override fun onOverview() {
        handler.postDelayed(clearOnOverview, 3000L)
        Statistics.onOverseen(did)
    }

    override fun onNoOverview() {
        handler.removeCallbacks(clearOnOverview)
    }

    override fun onSelected() {
        animate().setDuration(10000).alpha(.3f)
        currentDiscussion.deleteIntent?.send()

        Statistics.onSelected(did)
        val input = inputText
        if(input != null) {
            input.requestFocus()
            context.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    val mainColor = pickColor(did, 0)
    val secondaryColor = pickColor(did, 1)

    val scale = context.resources.displayMetrics.density
    var startFadeInTime = System.currentTimeMillis()

    private val paint = Paint().apply { isAntiAlias = true }

    private val overlayText: TextView
    private val inputText: EditText?
    var currentDiscussion: Discussion = discussion
    override fun updateDiscussion(d: Discussion) {
        currentDiscussion = d
        l("Updating text")
        overlayText.text = currentDiscussion.messages.map { it.msg }.joinToString("\n")
    }

    fun scaleColor(v: Int, scale: Float): Int {
        val a = ((v.toLong() and 0xff000000L).shr(24) * scale).toLong()
        return (
                (v.toLong() and 0x00ffffffL) or a.shl(24)
                ).toInt()
    }

    init {
        isClickable = true
        alpha = 0f
        rotation = 0f
    }

    private val overlayView = LinearLayout(context).apply {
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                val vy = velocityY / scale
                val vx = velocityX / scale
                l("fling $vx $vy")

                // vertical fling
                if(abs(vy) > 3 * abs(vx)) {
                    //Down to up
                    if(vy < 0) {
                        l("Got deleteintent ${discussion.deleteIntent}")
                        currentDiscussion.deleteIntent?.send()

                        val d = Discussions.map.remove(did)
                        l("Remove discussion $d")

                        val b = parent as? PopupContainer
                        b?.bar?.updateViews()
                        Statistics.onDismissed(did)
                    }
                }
                return true
            }
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                l("double tap")
                (parent as? ViewGroup)?.removeAllViews()

                //Sadly contentIntent can be cancelled, and often are.
                try {
                    currentDiscussion.contentIntent?.send()
                } catch(e: android.app.PendingIntent.CanceledException) {
                    l("Failed sending contentIntent because of canceld exception")

                }
                return true
            }
        })

        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(scaleColor(mainColor, 0.4f))

        addView(TextView(context).apply {
            text = did.person.nick
            background = resources.getDrawable(R.drawable.rounded_corners_rectangle)
                    .mutate()
                    .apply {
                        setColorFilter(scaleColor(secondaryColor, .1f), PorterDuff.Mode.DARKEN)
            }
            setPadding(
                    (20 * scale).toInt(),
                    (10 * scale).toInt(),
                    (20 * scale).toInt(),
                    (10 * scale).toInt()
            )
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setTextAppearance(R.style.TextAppearance_MaterialComponents_Headline6)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER})

        overlayText = TextView(context).apply {
            text = currentDiscussion.messages.map { it.msg }.joinToString("\n")
            setTextColor(Color.GRAY)
            textSize = 20.0f
        }
        addView(overlayText)
        if(currentDiscussion.replyAction != null) {
            inputText =
                    EditText(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setTextColor(Color.WHITE)
                        setSingleLine()
                        imeOptions = EditorInfo.IME_ACTION_DONE
                        setOnEditorActionListener(object : TextView.OnEditorActionListener {
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
                                    l("Sending!!!")
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
            addView(inputText)
        } else {
            inputText = null
        }

        val actions = currentDiscussion.actions
                .filter { it.remoteInputs == null || it.remoteInputs.isEmpty() }
        if(actions.isNotEmpty()) {
            val buttonsLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                for(action in actions) {
                    addView(Button(context).apply {
                        text = action.title
                        setOnClickListener {
                            action.actionIntent.send()
                        }
                    })
                }
            }
            addView(buttonsLayout)
        }
    }

    override fun getOverlayView(): View {
        return overlayView
    }

    fun unique(did: DiscussionId, id: Int, max: Int): Int {
        val h = did.hashCode() * when(id) {
            0 -> 4241
            1 -> 4591
            2 -> 6551
            3 -> 6029
            4 -> 7331
            5 -> 7177
            6 -> 6823
            else -> 7489
        }
        val i = (did.hashCode() * h) % max
        return if(i<0) i + max else i
    }

    fun pickColor(did: DiscussionId, id: Int): Int {
        val j = unique(did, id, 6)

        return when(j) {
            0 -> Color.BLUE
            1 -> Color.RED
            2 -> Color.YELLOW
            3 -> Color.GREEN
            4 -> Color.CYAN
            5 -> Color.MAGENTA
            else -> Color.GRAY
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.color = mainColor

        val radius = min(width, height) /2.5f
        canvas.drawCircle(width/2.0f, height/2.0f, radius, paint)

        val widthMargin = (width - 2f * radius) / 2f
        val heightMargin = (width - 2f * radius) / 2f
        paint.color = secondaryColor

        val centerAngle = when(unique(did, 2, 4)) {
            0 -> 0
            1 -> 90
            2 -> 180
            else -> 270
        }

        canvas.drawArc(
                widthMargin, heightMargin,
                width*1.0f - widthMargin, height*1.0f - heightMargin,
                centerAngle-30f, 60f, true, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(heightSize*1.4.toInt(), heightSize)
    }
}
