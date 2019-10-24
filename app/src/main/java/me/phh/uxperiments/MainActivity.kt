package me.phh.uxperiments

import android.app.Activity
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.provider.Settings
import android.text.Layout
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import kotlin.math.*

fun l(s: String) {
    android.util.Log.d("PHH-UX", s)
}

fun l(s: String, t: Throwable) {
    android.util.Log.d("PHH-UX", s, t)
    }

class MainActivity : Activity() {
    lateinit var grid: SelectorGrid
    lateinit var rootLayout: LinearLayout
    lateinit var popupContainer: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        popupContainer = LinearLayout(this)
        popupContainer.addView(
                TextView(this).also {
                    it.text="HELLO"
                    it.textSize=40.0f
                    it.setTextColor(Color.LTGRAY)
                })

        grid = SelectorGrid(this, popupContainer).also {
            it.orientation = LinearLayout.HORIZONTAL
            //it.rowCount = 1
            //it.columnCount = 10
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (50 * resources.displayMetrics.density).toInt()).also { it.gravity = Gravity.BOTTOM }
            it.setBackgroundColor(Color.BLACK)
            //it.useDefaultMargins = true
        }

        rootLayout = LinearLayout(this).also {
            it.orientation = LinearLayout.VERTICAL
            it.gravity = Gravity.BOTTOM
            it.addView(popupContainer)
            it.addView(grid)
        }

        setContentView(rootLayout)
    }

    object brightnessControl {
        var active = false
        var startX = -1.0f
        var startY = -1.0f
        var startBrightness = 0

        fun start(context: Context) {
            startBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            active = true
            startX = -1.0f
            startY = -1.0f
        }
    }

    val attentionScale = 0.6f
    val discussionOverlays = mutableMapOf<DiscussionId, SelectorView>()
    override fun onResume() {
        super.onResume()
        updateViews()
        Discussions.registerListener(object: Discussions.Listener {
            override fun onUpdated(did: DiscussionId) {
                updateViews()
            }
        })
    }

    fun updateViews() {
        grid.removeAllViews()

        for( (did, discussion) in Discussions.map) {
            var v: SelectorView? = null
            if(discussionOverlays.containsKey(did)) {
                l("Discussion already there, updating other one")
                v = discussionOverlays[did]
                v!!.updateDiscussion(discussion)
            } else {
                v = DiscussionOverlay(discussion, did, this)
                discussionOverlays[did] = v
            }
            grid.addView(v as View)
        }

        grid.addView(Space(this).also {
            it.layoutParams =
                    LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).also {
                        it.weight = 0.1f
                    }
        })

        grid.addView(
                object: ImageView(this), SelectorView {
                    val overlayView = object: View(context) {
                    }

                    override fun getOverlayView(): View {
                        return overlayView
                    }

                    override fun updateDiscussion(d: Discussion) {
                    }

                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        if(brightnessControl.active) {
                            if(event.action == MotionEvent.ACTION_UP) {
                                brightnessControl.active = false
                            }

                            if(brightnessControl.startX == -1.0f) {
                                brightnessControl.startX = event.x
                                brightnessControl.startY = event.y
                            } else {
                                val density = resources.displayMetrics.density
                                val dx = (event.x - brightnessControl.startX) / density
                                val dy = -(event.y - brightnessControl.startY) / density

                                val brightness = max(min(brightnessControl.startBrightness + (dx+dy), 255.0f),0.0f)
                                l("Setting brightness to $brightness")
                                Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness.toInt())
                            }
                            return true
                        }
                        if(event.action == MotionEvent.ACTION_DOWN) {
                            brightnessControl.start(this@MainActivity)
                            return true
                        }
                        return true
                    }
                }.also {
                    it.setImageResource( R.drawable.brightness_icon)
                    val newColor = arrayOf(255.0f, 255.0f, 200.0f).map { it * attentionScale }
                    val matrix =
                            arrayOf(
                                    0.0f, 0.0f, 0.0f, 0.0f, newColor[0],
                                    0.0f, 0.0f, 0.0f, 0.0f, newColor[1],
                                    0.0f, 0.0f, 0.0f, 0.0f, newColor[2],
                                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                            )
                    it.colorFilter = ColorMatrixColorFilter(matrix.toFloatArray())
                    it.adjustViewBounds = true
                    it.scaleType = ImageView.ScaleType.FIT_XY
                    it.layoutParams = GridLayout.LayoutParams(
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                                )
                    ).also {
                        it.rowSpec = GridLayout.spec(0)
                        it.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.END)
                        it.setGravity(Gravity.RIGHT or Gravity.FILL)
                    }
                    it.setOnClickListener {
                        brightnessControl.start(this)
                    }
                }
        )

    }

}
