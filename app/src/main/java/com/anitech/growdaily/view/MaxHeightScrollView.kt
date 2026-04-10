package com.anitech.growdaily.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView
import com.anitech.growdaily.R

class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var maxHeight = -1

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView)
        maxHeight = a.getDimensionPixelSize(R.styleable.MaxHeightScrollView_maxHeight, -1)
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var hSpec = heightMeasureSpec
        if (maxHeight > 0) {
            hSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, hSpec)
    }

    fun setMaxHeight(height: Int) {
        maxHeight = height
        requestLayout()
    }
}
