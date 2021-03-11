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

import android.content.pm.ActivityInfo;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.android.pictureinpicture.widget.MovieView;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void movie_playingOnPip() {
        // The movie should be playing on start
        onView(withId(R.id.movie))
                .check(matches(allOf(isDisplayed(), isPlaying())))
                .perform(showControls());
        // Click on the button to enter Picture-in-Picture mode
        onView(withId(R.id.minimize)).perform(click());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // The Activity is paused. We cannot use Espresso to test paused activities.
        rule.getScenario().onActivity(activity -> {
            // We are now in Picture-in-Picture mode
            assertTrue(activity.isInPictureInPictureMode());
            final MovieView view = activity.findViewById(R.id.movie);
            assertNotNull(view);
            // The video should still be playing
            assertTrue(view.isPlaying());
        });
    }

    @Test
    public void movie_pauseAndResume() {
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
    public void fullscreen_enabledOnLandscape() {
        rule.getScenario().onActivity(activity ->
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        rule.getScenario().onActivity(activity -> {
            final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(
                    activity.getWindow().getDecorView()
            );
            final Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            assertThat(systemBars.left, is(0));
            assertThat(systemBars.top, is(0));
            assertThat(systemBars.right, is(0));
            assertThat(systemBars.bottom, is(0));
        });
    }

    @Test
    public void fullscreen_disabledOnPortrait() {
        rule.getScenario().onActivity(activity ->
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        rule.getScenario().onActivity(activity -> {
            final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(
                    activity.getWindow().getDecorView()
            );
            final Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            assertThat(systemBars.top, is(not(0)));
            assertThat(systemBars.bottom, is(not(0)));
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
}
