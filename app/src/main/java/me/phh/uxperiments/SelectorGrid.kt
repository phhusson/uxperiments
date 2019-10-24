package me.phh.uxperiments

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout

interface SelectorView  {
    fun getOverlayView(): View
    fun updateDiscussion(d: Discussion)
}

class SelectorGrid(context: Context, val container: LinearLayout) : LinearLayout(context) {
    fun children(): List<View> {
        return (0 until childCount).map { getChildAt(it) }
    }

    var currentSelected: SelectorView? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val r = Rect()

        if(ev.action == MotionEvent.ACTION_UP) {
            currentSelected = null
            return true
        }

        for(touchable in touchables) {
            touchable.getHitRect(r)
            if(ev.x >= r.left &&
                    ev.x <= r.right &&
                    ev.y >= r.top &&
                    ev.y <= bottom) {

                if(touchable is SelectorView) {
                    if(currentSelected == touchable) {
                        return super.dispatchTouchEvent(ev)
                    }
                    currentSelected = touchable
                    val v = touchable.getOverlayView()
                    container.removeAllViews()
                    container.addView(v)
                }
                return super.dispatchTouchEvent(ev)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}