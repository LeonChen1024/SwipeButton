package com.leonchen1024.swipebutton

import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val proSwipeBtn = findViewById<SwipeButton>(R.id.proswipebutton_main)
        val proSwipeBtnError = findViewById<SwipeButton>(R.id.proswipebutton_main_error)
        proSwipeBtn.swipeDistanceRatio = 0.5f

        proSwipeBtn.setOnSwipeListener(object : SwipeButton.OnSwipeListener {
            override fun onSwipeConfirm() {
                // user has swiped the btn. Perform your async operation now
                Handler().postDelayed({ proSwipeBtn.showResultIcon(true, false) }, 2000)
            }
        })

        proSwipeBtnError.setOnSwipeListener(object : SwipeButton.OnSwipeListener {
            override fun onSwipeConfirm() {
                // user has swiped the btn. Perform your async operation now
                Handler().postDelayed({ proSwipeBtnError.showResultIcon(false, true) }, 2000)
            }
        })


    }

}
