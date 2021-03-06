/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.EngineView
import java.lang.ref.WeakReference

/**
 * Handles properly animating the browser engine based on `SHOULD_ANIMATE_FLAG` passed in through
 * nav arguments.
 */
class BrowserAnimator(
    private val fragment: WeakReference<Fragment>,
    private val engineView: WeakReference<EngineView>,
    private val swipeRefresh: WeakReference<View>,
    private val arguments: Bundle
) {

    private val viewLifeCycleScope: LifecycleCoroutineScope?
        get() = fragment.get()?.viewLifecycleOwner?.lifecycleScope

    private val unwrappedEngineView: EngineView?
        get() = engineView.get()

    private val unwrappedSwipeRefresh: View?
        get() = swipeRefresh.get()

    /**
     * Triggers the browser animation to run if necessary (based on the SHOULD_ANIMATE_FLAG). Also
     * removes the flag from the bundle so it is not played on future entries into the fragment.
     */
    fun beginAnimationIfNecessary() {
        val shouldAnimate = arguments.getBoolean(SHOULD_ANIMATE_FLAG, false)
        if (shouldAnimate) {
            viewLifeCycleScope?.launch(Dispatchers.Main) {
                delay(ANIMATION_DELAY)
                captureEngineViewAndDrawStatically {
                    animateBrowserEngine(unwrappedSwipeRefresh)
                }
            }
        } else {
            unwrappedSwipeRefresh?.alpha = 1f
            unwrappedEngineView?.asView()?.visibility = View.VISIBLE
            unwrappedSwipeRefresh?.background = null
        }
    }

    /**
     * Details exactly how the browserEngine animation should look and plays it.
     */
    private fun animateBrowserEngine(browserEngine: View?) {
        val valueAnimator = ValueAnimator.ofFloat(0f, END_ANIMATOR_VALUE)

        valueAnimator.addUpdateListener {
            browserEngine?.scaleX = STARTING_XY_SCALE + XY_SCALE_MULTIPLIER * it.animatedFraction
            browserEngine?.scaleY = STARTING_XY_SCALE + XY_SCALE_MULTIPLIER * it.animatedFraction
            browserEngine?.alpha = it.animatedFraction
        }

        valueAnimator.doOnEnd {
            unwrappedEngineView?.asView()?.visibility = View.VISIBLE
            unwrappedSwipeRefresh?.background = null
            arguments.putBoolean(SHOULD_ANIMATE_FLAG, false)
        }

        valueAnimator.interpolator = DecelerateInterpolator()
        valueAnimator.duration = ANIMATION_DURATION
        valueAnimator.start()
    }

    /**
     * Makes the swipeRefresh background a screenshot of the engineView in its current state.
     * This allows us to "animate" the engineView.
     */
    fun captureEngineViewAndDrawStatically(onComplete: () -> Unit) {
        unwrappedEngineView?.asView()?.context.let {
            viewLifeCycleScope?.launch {
                // isAdded check is necessary because of a bug in viewLifecycleOwner. See AC#3828
                if (!fragment.isAdded()) { return@launch }
                unwrappedEngineView?.captureThumbnail { bitmap ->
                    if (!fragment.isAdded()) { return@captureThumbnail }

                    unwrappedSwipeRefresh?.apply {
                        alpha = 0f
                        // If the bitmap is null, the best we can do to reduce the flash is set transparent
                        background = bitmap?.toDrawable(context.resources)
                            ?: ColorDrawable(Color.TRANSPARENT)
                    }

                    onComplete()
                }
            }
        }
    }

    private fun WeakReference<Fragment>.isAdded(): Boolean {
        val unwrapped = get()
        return unwrapped != null && unwrapped.isAdded
    }

    companion object {
        private const val SHOULD_ANIMATE_FLAG = "shouldAnimate"
        private const val ANIMATION_DELAY = 50L
        private const val ANIMATION_DURATION = 115L
        private const val END_ANIMATOR_VALUE = 500f
        private const val XY_SCALE_MULTIPLIER = .05f
        private const val STARTING_XY_SCALE = .95f
    }
}
