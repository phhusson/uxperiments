package me.phh.uxperiments

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout

interface SelectorView  {
    fun getOverlayView(): View?
    fun updateDiscussion(d: Discussion)
    fun onSelected()
}

class SelectorGrid(context: Context, val container: LinearLayout) : LinearLayout(context) {
    fun children(): List<View> {
        return (0 until childCount).map { getChildAt(it) }
    }

    var currentSelected: SelectorView? = null
    var wasOnBar = false
    var hasMoved = false
    var startedGesture = false

    init {
        isFocusable = false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val r = Rect()

        if(ev.action == MotionEvent.ACTION_UP) {
            startedGesture = false
        }

        if(ev.action == MotionEvent.ACTION_UP && wasOnBar && hasMoved) {
            wasOnBar = false
            currentSelected = null
            container.removeAllViews()
            return true
        }

        var gotOneY = false
        for(touchable in touchables) {
            touchable.getHitRect(r)
            val isInX = ev.x >= r.left && ev.x <= r.right
            val isInY = ev.y >= r.top && ev.y <= bottom
            val isDownOrMoving = ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE
            gotOneY = gotOneY or isInY
            if(isInX && isInY && isDownOrMoving &&!startedGesture) {
                wasOnBar = true
                hasMoved = hasMoved || ev.action == MotionEvent.ACTION_MOVE
                if(touchable is SelectorView) {
                    if(currentSelected == touchable) {
                        return true
                    }

                    currentSelected = touchable
                    val v = touchable.getOverlayView()
                    container.removeAllViews()
                    if(v != null) {
                        container.addView(v)
                    }
                    return true
                }
                return true
            }

            //Swiping up from bar
            if(isInX && wasOnBar && ev.action == MotionEvent.ACTION_MOVE && ev.y < (top-60)) {
                l("Swiped up")
                startedGesture = true

                val fakeEvent = MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN,
                        (r.left + r.right)/2f,
                        (r.top + r.bottom)/2f,
                        0
                )
                currentSelected?.onSelected()
                super.dispatchTouchEvent(fakeEvent)
                wasOnBar = false
            }
        }
        if(gotOneY) return true
        return super.dispatchTouchEvent(ev)
    }
}