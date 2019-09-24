/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.pictureinpicture

import android.content.pm.ActivityInfo
import android.support.test.InstrumentationRegistry
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.UiController
import android.support.test.espresso.ViewAction
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers.isAssignableFrom
import android.support.test.espresso.matcher.ViewMatchers.isDisplayed
import android.support.test.espresso.matcher.ViewMatchers.withId
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.view.View
import com.example.android.pictureinpicture.widget.MovieView
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.core.AllOf.allOf
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Rule @JvmField
    val rule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun movie_playingOnPip() {
        // The movie should be playing on start
        onView(withId(R.id.movie))
                .check(matches(allOf(isDisplayed(), isPlaying())))
                .perform(showControls())
        // Click on the button to enter Picture-in-Picture mode
        onView(withId(R.id.minimize)).perform(click())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // The Activity is paused. We cannot use Espresso to test paused activities.
        rule.runOnUiThread {
            // We are now in Picture-in-Picture mode
            assertTrue(rule.activity.isInPictureInPictureMode)
            val view = rule.activity.findViewById<MovieView>(R.id.movie)
            assertNotNull(view)
            // The video should still be playing
            assertTrue(view.isPlaying)
        }
    }

    @Test
    fun movie_pauseAndResume() {
        // The movie should be playing on start
        onView(withId(R.id.movie))
                .check(matches(allOf(isDisplayed(), isPlaying())))
                .perform(showControls())
        // Pause
        onView(withId(R.id.toggle)).perform(click())
        onView(withId(R.id.movie)).check(matches(not(isPlaying())))
        // Resume
        onView(withId(R.id.toggle)).perform(click())
        onView(withId(R.id.movie)).check(matches(isPlaying()))
    }

    @Test
    fun fullscreen_enabledOnLandscape() {
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        rule.runOnUiThread {
            val decorView = rule.activity.window.decorView
            assertThat(decorView.systemUiVisibility,
                    hasFlag(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN))
        }
    }

    @Test
    fun fullscreen_disabledOnPortrait() {
        rule.runOnUiThread {
            rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        rule.runOnUiThread {
            val decorView = rule.activity.window.decorView
            assertThat(decorView.systemUiVisibility,
                    not(hasFlag(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)))
        }
    }

    private fun isPlaying(): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            override fun matchesSafely(view: View): Boolean {
                return (view as MovieView).isPlaying
            }

            override fun describeTo(description: Description) {
                description.appendText("MovieView is playing")
            }
        }
    }

    private fun showControls(): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isAssignableFrom(MovieView::class.java)
            }

            override fun getDescription(): String {
                return "Show controls of MovieView"
            }

            override fun perform(uiController: UiController, view: View) {
                uiController.loopMainThreadUntilIdle()
                (view as MovieView).showControls()
                uiController.loopMainThreadUntilIdle()
            }
        }
    }

    private fun hasFlag(flag: Int): Matcher<in Int> {
        return object : TypeSafeMatcher<Int>() {
            override fun matchesSafely(i: Int?): Boolean {
                return i?.and(flag) == flag
            }

            override fun describeTo(description: Description) {
                description.appendText("Flag integer contains " + flag)
            }
        }
    }

}
