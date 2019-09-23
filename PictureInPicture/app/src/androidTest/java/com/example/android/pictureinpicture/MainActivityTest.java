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

package com.example.android.pictureinpicture;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import com.example.android.pictureinpicture.widget.MovieView;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);

    @Test
    public void movie_playingOnPip() throws Throwable {
        // The movie should be playing on start
        onView(withId(R.id.movie))
                .check(matches(allOf(isDisplayed(), isPlaying())))
                .perform(showControls());
        // Click on the button to enter Picture-in-Picture mode
        onView(withId(R.id.minimize)).perform(click());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // The Activity is paused. We cannot use Espresso to test paused activities.
        rule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // We are now in Picture-in-Picture mode
                assertTrue(rule.getActivity().isInPictureInPictureMode());
                final MovieView view = rule.getActivity().findViewById(R.id.movie);
                assertNotNull(view);
                // The video should still be playing
                assertTrue(view.isPlaying());
            }
        });
    }

    @Test
    public void movie_pauseAndResume() throws Throwable {
        // The movie should be playing on start
        onView(withId(R.id.movie))
                .check(matches(allOf(isDisplayed(), isPlaying())))
                .perform(showControls());
        // Pause
        onView(withId(R.id.toggle)).perform(click());
        onView(withId(R.id.movie)).check(matches((not(isPlaying()))));
        // Resume
        onView(withId(R.id.toggle)).perform(click());
        onView(withId(R.id.movie)).check(matches(isPlaying()));
    }

    @Test
    public void fullscreen_enabledOnLandscape() throws Throwable {
        rule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rule.getActivity()
                        .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        rule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final View decorView = rule.getActivity().getWindow().getDecorView();
                assertThat(decorView.getSystemUiVisibility(),
                        hasFlag(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN));
            }
        });
    }

    @Test
    public void fullscreen_disabledOnPortrait() throws Throwable {
        rule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rule.getActivity()
                        .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        rule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final View decorView = rule.getActivity().getWindow().getDecorView();
                assertThat(decorView.getSystemUiVisibility(),
                        not(hasFlag(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)));
            }
        });
    }

    private static Matcher<? super View> isPlaying() {
        return new TypeSafeMatcher<View>() {
            @Override
            protected boolean matchesSafely(View view) {
                return ((MovieView) view).isPlaying();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("MovieView is playing");
            }
        };
    }

    private static ViewAction showControls() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isAssignableFrom(MovieView.class);
            }

            @Override
            public String getDescription() {
                return "Show controls of MovieView";
            }

            @Override
            public void perform(UiController uiController, View view) {
                uiController.loopMainThreadUntilIdle();
                ((MovieView) view).showControls();
                uiController.loopMainThreadUntilIdle();
            }
        };
    }

    private static Matcher<? super Integer> hasFlag(final int flag) {
        return new TypeSafeMatcher<Integer>() {
            @Override
            protected boolean matchesSafely(Integer i) {
                return (i & flag) == flag;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Flag integer contains " + flag);
            }
        };
    }

}
