package com.leonchen1024.swipebutton

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.annotation.Nullable
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat

class SwipeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val HINT_BACK_DURATION: Long = 300
        private const val INIT_HINT_DURATION: Long = 300
        private val DEFAULT_TEXT_SIZE: Float = UiUtils.dpToPx(14).toFloat()
        private const val DEFAULT_SWIPE_DISTANCE: Float = 0.85f
        private val BTN_INIT_RADIUS: Float = UiUtils.dpToPx(3).toFloat()
        private val BTN_MORPHED_RADIUS = UiUtils.dpToPx(100).toFloat()
        private const val MORPH_ANIM_DURATION: Long = 500
    }

    private val root: View
    private val gradientDrawable: GradientDrawable
    private val contentContainer: RelativeLayout
    private val contentTv: TextView
    private val arrow1: ImageView
    private val arrow2: ImageView
    private val arrowHintContainer: LinearLayout
    private var progressBar: ProgressBar? = null

    @ColorInt
    private var textColorInt: Int = 0

    private var btnText: CharSequence = ""

    @ColorInt
    private var bgColorInt: Int = 0
    @ColorInt
    private var arrowColorRes: Int = 0
    private var cornerRadius: Float = BTN_INIT_RADIUS
    @Dimension
    private var textSize: Float = DEFAULT_TEXT_SIZE
    @Nullable
    private var swipeListener: OnSwipeListener? = null

    @get:Dimension
    var swipeDistanceRatio = DEFAULT_SWIPE_DISTANCE
        /**
         * How much distance of the button that user should swipe to trigger the
         * OnSwipeListener successfully
         *
         * @param swipeDistance float from 0.0 to 1.0 where 1.0 means user must
         * swipe the button fully from start to end. Default is 0.85.
         */
        set(@Dimension swipeDistance) {
            if (swipeDistance > 1.0f) {
                field = 1.0f
            }
            if (swipeDistance < 0.0f) {
                field = 0.0f
            }
        }

    init {
        val a = context.theme.obtainStyledAttributes(
            attrs, R.styleable.SwipeButton, 0, 0
        )
        try {
            val btnString = a.getString(R.styleable.SwipeButton_btn_text)
            if (btnString != null)
                btnText = btnString
            textColorInt = a.getColor(
                R.styleable.SwipeButton_text_color,
                ContextCompat.getColor(context, R.color.swipe_btn_default_text_color)
            )
            bgColorInt = a.getColor(
                R.styleable.SwipeButton_bg_color,
                ContextCompat.getColor(context, R.color.swipe_btn_default_background)
            )
            arrowColorRes = a.getColor(
                R.styleable.SwipeButton_arrow_color,
                ContextCompat.getColor(context, R.color.swipe_btn_default_arrow_color)
            )
            cornerRadius =
                a.getFloat(R.styleable.SwipeButton_btn_radius, BTN_INIT_RADIUS)
            textSize = a.getDimensionPixelSize(
                R.styleable.SwipeButton_text_size, DEFAULT_TEXT_SIZE.toInt()
            ).toFloat()
        } finally {
            a.recycle()
        }

        val inflater = LayoutInflater.from(context)
        root = inflater.inflate(R.layout.view_proswipebtn, this, true)
        contentContainer = root.findViewById(R.id.rlSwipeBtnContainer)
        arrowHintContainer = root.findViewById(R.id.llSwipeBtnHintContainer)
        contentTv = root.findViewById(R.id.tvBtnText)
        arrow1 = root.findViewById(R.id.ivArrow1)
        arrow2 = root.findViewById(R.id.ivArrow2)

        tintArrowHint()
        contentTv.text = btnText
        contentTv.setTextColor(textColorInt)
        contentTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        gradientDrawable = GradientDrawable()
        gradientDrawable.shape = GradientDrawable.RECTANGLE
        gradientDrawable.cornerRadius = cornerRadius
        setBackgroundColor(bgColorInt)
        setupTouchListener()
        post {
            startActionTipAnim()
        }
    }

    fun setButtonText(text: String) {
        btnText = text
        contentTv.text = text
    }

    fun setTextColor(@ColorInt textColor: Int) {
        textColorInt = textColor
        contentTv.setTextColor(textColor)
    }

    private fun setupTouchListener() {
        setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> return true
                    MotionEvent.ACTION_MOVE -> {
                        // Movement logic here
                        if (event.x > arrowHintContainer.width / 2 &&
                            event.x + arrowHintContainer.width / 2 < width &&
                            (event.x < arrowHintContainer.x + arrowHintContainer.width ||
                                    arrowHintContainer.x != 0f)
                        ) {
                            // snaps the hint to user touch, only if the touch is within
                            // (hint width/2) or if it has already been displaced.the finger
                            // position is in the middle of the arrow container.
                            arrowHintContainer.x = event.x - arrowHintContainer.width / 2
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        //Release logic here
                        if (arrowHintContainer.x + arrowHintContainer.width >
                            width * swipeDistanceRatio
                        ) {
                            // swipe completed, fly the hint away!
                            performSuccessfulSwipe()
                        } else if (arrowHintContainer.x <= 0) {
                            // upon click without swipe
                            startActionTipAnim()
                        } else {
                            // swipe not completed, pull back the hint
                            animateHintBack()
                        }
                        v.performClick()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun performSuccessfulSwipe() {
        swipeListener?.onSwipeConfirm()
        morphToCircle()
    }

    private fun animateHintBack() {
        val positionAnimator = ValueAnimator.ofFloat(arrowHintContainer.x, 0F)
        positionAnimator.interpolator = AccelerateDecelerateInterpolator()
        positionAnimator.addUpdateListener {
            val x = positionAnimator.animatedValue as Float
            arrowHintContainer.x = x
        }

        positionAnimator.duration = HINT_BACK_DURATION
        positionAnimator.start()
    }

    private fun startActionTipAnim() {
        if (isEnabled) {
            val animation = TranslateAnimation(
                0f, measuredWidth.toFloat(), 0f, 0f
            )
            animation.interpolator = AccelerateDecelerateInterpolator()
            animation.duration = 1000
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}

                override fun onAnimationEnd(animation: Animation) {
                    startHintInitAnim()
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
            arrowHintContainer.startAnimation(animation)
        }
    }

    /**
     * animate entry of hint from the left-most edge
     */
    private fun startHintInitAnim() {
        val anim = TranslateAnimation((-arrowHintContainer.width).toFloat(), 0f, 0f, 0f)
        anim.duration = INIT_HINT_DURATION
        arrowHintContainer.startAnimation(anim)
    }

    /**
     * Just like performOnClick() in a standard button, this will call the attached OnSwipeListener
     * and morph the btn to a circle
     */
    fun performOnSwipe() {
        performSuccessfulSwipe()
    }

    private fun morphToCircle() {
        UiUtils.animateFadeHide(context, arrowHintContainer)
        // prevent user touch btn in trans duration cause problem
        setOnTouchListener(null)

        val cornerAnimation = ObjectAnimator.ofFloat(
            gradientDrawable, "cornerRadius",
            BTN_INIT_RADIUS, BTN_MORPHED_RADIUS
        )
        val circleRadius = getWaitingRadius()
        UiUtils.animateFadeHide(context, contentTv)
        val widthAnimation: ValueAnimator = ValueAnimator.ofInt(width, circleRadius)
        widthAnimation.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Int
            val layoutParams = contentContainer.layoutParams
            layoutParams.width = value
            contentContainer.layoutParams = layoutParams
        }
        val heightAnimation = ValueAnimator.ofInt(height, circleRadius)
        heightAnimation.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Int
            val layoutParams = contentContainer.layoutParams
            layoutParams.height = value
            contentContainer.layoutParams = layoutParams
        }

        val animatorSet = AnimatorSet()
        animatorSet.duration = MORPH_ANIM_DURATION
        animatorSet.playTogether(cornerAnimation, widthAnimation, heightAnimation)
        animatorSet.start()

        showProgressBar()
    }

    private fun getWaitingRadius() = if (width > height) height else width

    private fun morphToRect() {
        setupTouchListener()
        val cornerAnimation = ObjectAnimator.ofFloat(
            gradientDrawable, "cornerRadius",
            BTN_MORPHED_RADIUS, BTN_INIT_RADIUS
        )
        val circleRadius = getWaitingRadius()
        val widthAnimation: ValueAnimator = ValueAnimator.ofInt(circleRadius, width)
        widthAnimation.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Int
            val layoutParams = contentContainer.layoutParams
            layoutParams.width = value
            contentContainer.layoutParams = layoutParams
        }
        val heightAnimation = ValueAnimator.ofInt(circleRadius, height)
        heightAnimation.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Int
            val layoutParams = contentContainer.layoutParams
            layoutParams.height = value
            contentContainer.layoutParams = layoutParams
        }

        val animatorSet = AnimatorSet()
        animatorSet.duration = MORPH_ANIM_DURATION
        animatorSet.playTogether(cornerAnimation, widthAnimation, heightAnimation)
        animatorSet.start()
    }

    private fun updateBackground() {
        contentContainer.background = gradientDrawable
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            gradientDrawable.setColor(
                ContextCompat.getColor(context, R.color.proswipebtn_disabled_grey)
            )
            updateBackground()
            this.alpha = 0.5f
        } else {
            setBackgroundColor(bgColorInt)
            this.alpha = 1f
        }
    }

    private fun showProgressBar() {
        progressBar = ProgressBar(context)
        progressBar!!.indeterminateDrawable.setColorFilter(
            ContextCompat.getColor(context, android.R.color.white), PorterDuff.Mode.SRC_IN
        )
        UiUtils.animateFadeHide(context, contentTv)
        contentContainer.addView(progressBar)
    }

    @JvmOverloads
    fun showResultIcon(isSuccess: Boolean, shouldReset: Boolean = !isSuccess) {
        progressBar?.let { UiUtils.animateFadeHide(context, it) }

        val resultIcon = AppCompatImageView(context)
        val radius = getWaitingRadius()
        resultIcon.layoutParams = LayoutParams(radius, radius)
        resultIcon.visibility = View.GONE
        val icon: Int = if (isSuccess)
            R.drawable.ic_check_circle_36dp
        else
            R.drawable.ic_cancel_full_24dp
        resultIcon.setImageResource(icon)
        contentContainer.addView(resultIcon)
        UiUtils.animateFadeShow(context, resultIcon)

        if (shouldReset) {
            // expand the btn again
            postDelayed({
                UiUtils.animateFadeHide(context, resultIcon)
                morphToRect()
                arrowHintContainer.x = 0f
                UiUtils.animateFadeShow(context, arrowHintContainer)
                UiUtils.animateFadeShow(context, contentTv)
            }, 1200)
        }
    }

    private fun tintArrowHint() {
        arrow1.setColorFilter(arrowColorRes, PorterDuff.Mode.MULTIPLY)
        arrow2.setColorFilter(arrowColorRes, PorterDuff.Mode.MULTIPLY)
    }

    interface OnSwipeListener {
        fun onSwipeConfirm()
    }

    override fun setBackgroundColor(@ColorInt bgColor: Int) {
        this.bgColorInt = bgColor
        gradientDrawable.setColor(bgColor)
        updateBackground()
    }

    /**
     * Include alpha in arrowColor for transparency (ex: #33FFFFFF)
     */
    fun setArrowColor(arrowColor: Int) {
        this.arrowColorRes = arrowColor
        tintArrowHint()
    }

    fun setTextSize(@Dimension textSize: Float) {
        this.textSize = textSize
        contentTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
    }

    fun setOnSwipeListener(@Nullable customSwipeListener: OnSwipeListener) {
        this.swipeListener = customSwipeListener
    }

}
