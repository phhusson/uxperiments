package me.phh.uxperiments

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.provider.Settings
import android.view.*
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import kotlin.math.max
import kotlin.math.min


class PopupContainer(context: Context, val bar: Bar) : LinearLayout(context) {
    val wm = context.getSystemService(WindowManager::class.java)
    override fun removeAllViews() {
        super.removeAllViews()
        bar.grid.currentSelected = null

        val p = NotificationService.popupParams
        p.flags =
                NotificationService.popupParams.flags or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wm.updateViewLayout(this, p)
    }

    override fun addView(child: View) {
        val new = (childCount == 0)
        super.addView(child)

        if(!new) return

        val p = NotificationService.popupParams
        p.flags =
                NotificationService.popupParams.flags and
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        wm.updateViewLayout(this, p)
    }
}

class Bar(context: Context, includePopup: Boolean = true) : LinearLayout(context) {
    val grid: SelectorGrid
    val popupContainer: LinearLayout
    val discussionOverlays = mutableMapOf<DiscussionId, SelectorView>()

    val barHeight = (30 * resources.displayMetrics.density).toInt()
    init {
        popupContainer = PopupContainer(context, this)

        grid = SelectorGrid(context, popupContainer).also {
            it.orientation = LinearLayout.HORIZONTAL
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeight).also { it.gravity = Gravity.BOTTOM }
            it.setBackgroundColor(Color.BLACK)
        }

        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        if(includePopup) addView(popupContainer)
        addView(grid)
        updateViews()
        Discussions.registerListener(object: Discussions.Listener {
            override fun onUpdated(did: DiscussionId) {
                updateViews()
            }
        })
    }

    object BrightnessControl {
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

    fun updateViews() {
        grid.removeAllViews()

        if(popupContainer.childCount > 0)
            popupContainer.removeAllViews()

        for( (did, discussion) in Discussions.map) {
            var v: SelectorView?
            if(discussionOverlays.containsKey(did)) {
                l("Discussion already there, updating other one")
                v = discussionOverlays[did]
                v!!.updateDiscussion(discussion)
            } else {
                v = DiscussionOverlay(discussion, did, context)
                discussionOverlays[did] = v
                if(!NotificationService.initing) {
                    l("Animating ${System.identityHashCode(v)}!", Exception())
                    v
                            .animate()
                            .setDuration(10000)
                            .alpha(1f)
                            .withStartAction {
                                l("Starting animation on ${System.identityHashCode(v)}!")
                            }
                            .withEndAction {
                                l("Finishing animation on ${System.identityHashCode(v)}!")
                            }
                } else {
                    v.alpha = 1f
                }
            }
            grid.addView(v as View)
        }

        //Spacing to align on the right
        grid.addView(Space(context).also {
            it.layoutParams =
                    LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    ).also {
                        it.weight = 0.1f
                    }
        })

        //Brightness control gesture
        grid.addView(
                object: ImageView(context), SelectorView {
                    override fun onSelected() {
                    }

                    override fun getOverlayView(): View? {
                        return null
                    }

                    override fun updateDiscussion(d: Discussion) {
                    }

                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        if(event.action != MotionEvent.ACTION_MOVE && BrightnessControl.active) {
                            BrightnessControl.active = false
                            return true
                        }
                        if(BrightnessControl.active) {
                            if(BrightnessControl.startX == -1.0f) {
                                BrightnessControl.startX = event.x
                                BrightnessControl.startY = event.y
                            } else {
                                val density = resources.displayMetrics.density
                                val dx = (event.x - BrightnessControl.startX) / density
                                val dy = -(event.y - BrightnessControl.startY) / density

                                val brightness = max(min(BrightnessControl.startBrightness + (dx+dy), 255.0f),0.0f)
                                l("Setting brightness to $brightness")
                                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness.toInt())
                            }
                            return true
                        }
                        if(event.action == MotionEvent.ACTION_DOWN) {
                            BrightnessControl.start(context)
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
                        BrightnessControl.start(context)
                    }
                }
        )

    }

}