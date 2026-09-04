package com.pockethub.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Central haptic vocabulary. The app previously shipped entirely silent —
 * every commit, swipe and selection landed without tactile confirmation,
 * which reads as "plastic". These four cover the whole interaction space;
 * keep the mapping consistent app-wide:
 *
 *  - [tick]      light selection feedback — tab switch, pressable cards, chips
 *  - [confirm]   an action committed — star, mark done, refresh triggered
 *  - [reject]    action refused / failure surfaced
 *  - [longPress] popup / drag handles opened
 */
object Haptics {

    private const val SDK_FOR_CONFIRM = Build.VERSION_CODES.R

    /** Light selection tick. Safe to fire often. */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Committed-action confirmation buzz. */
    fun confirm(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= SDK_FOR_CONFIRM) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    /** Rejection / failure double-buzz (API 30+; long-press fallback below). */
    fun reject(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= SDK_FOR_CONFIRM) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
        )
    }

    /** Heavy feedback for long-press menus and drag handles. */
    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
