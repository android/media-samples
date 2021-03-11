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

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Rational;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.android.pictureinpicture.widget.MovieView;

/**
 * Demonstrates usage of Picture-in-Picture when using {@link MediaSessionCompat}.
 */
public class MediaSessionPlaybackActivity extends AppCompatActivity {

    private static final String TAG = "MediaSessionPlaybackActivity";

    public static final long MEDIA_ACTIONS_PLAY_PAUSE =
            PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE;

    public static final long MEDIA_ACTIONS_ALL =
            MEDIA_ACTIONS_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

    private MediaSessionCompat mSession;

    /**
     * The arguments to be used for Picture-in-Picture mode.
     */
    private final PictureInPictureParams.Builder mPictureInPictureParamsBuilder =
            new PictureInPictureParams.Builder();

    /**
     * This shows the video.
     */
    private MovieView mMovieView;

    /**
     * The bottom half of the screen; hidden on landscape
     */
    private ScrollView mScrollView;

    private final View.OnClickListener mOnClickListener = view -> {
        if (view.getId() == R.id.pip) {
            minimize();
        }
    };

    /**
     * Callbacks from the {@link MovieView} showing the video playback.
     */
    private final MovieView.MovieListener mMovieListener = new MovieView.MovieListener() {

        @Override
        public void onMovieStarted() {
            // We are playing the video now. Update the media session state and the PiP
            // window will update the actions.
            updatePlaybackState(
                    PlaybackStateCompat.STATE_PLAYING,
                    mMovieView.getCurrentPosition(),
                    mMovieView.getVideoResourceId());
        }

        @Override
        public void onMovieStopped() {
            // The video stopped or reached its end. Update the media session state and the
            // PiP window will update the actions.
            updatePlaybackState(
                    PlaybackStateCompat.STATE_PAUSED,
                    mMovieView.getCurrentPosition(),
                    mMovieView.getVideoResourceId());
        }

        @Override
        public void onMovieMinimized() {
            // The MovieView wants us to minimize it. We enter Picture-in-Picture mode now.
            minimize();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // View references
        mMovieView = findViewById(R.id.movie);
        mScrollView = findViewById(R.id.scroll);
        Button switchExampleButton = findViewById(R.id.switch_example);
        switchExampleButton.setText(getString(R.string.switch_custom));
        switchExampleButton.setOnClickListener(new SwitchActivityOnClick());

        // Set up the video; it automatically starts.
        mMovieView.setMovieListener(mMovieListener);
        findViewById(R.id.pip).setOnClickListener(mOnClickListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializeMediaSession();
    }

    private void initializeMediaSession() {
        mSession = new MediaSessionCompat(this, TAG);
        mSession.setActive(true);
        MediaControllerCompat.setMediaController(this, mSession.getController());

        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, mMovieView.getTitle())
                .build();
        mSession.setMetadata(metadata);

        MediaSessionCallback mMediaSessionCallback = new MediaSessionCallback(mMovieView);
        mSession.setCallback(mMediaSessionCallback);

        int state =
                mMovieView.isPlaying()
                        ? PlaybackStateCompat.STATE_PLAYING
                        : PlaybackStateCompat.STATE_PAUSED;
        updatePlaybackState(
                state,
                MEDIA_ACTIONS_ALL,
                mMovieView.getCurrentPosition(),
                mMovieView.getVideoResourceId());
    }

    @Override
    protected void onStop() {
        super.onStop();
        // On entering Picture-in-Picture mode, onPause is called, but not onStop.
        // For this reason, this is the place where we should pause the video playback.
        mMovieView.pause();
        mSession.release();
        mSession = null;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (!isInPictureInPictureMode()) {
            // Show the video controls so the video can be easily resumed.
            mMovieView.showControls();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustFullScreen(newConfig);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            adjustFullScreen(getResources().getConfiguration());
        }
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode, Configuration configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, configuration);
        if (!isInPictureInPictureMode) {
            // Show the video controls if the video is not playing
            if (mMovieView != null && !mMovieView.isPlaying()) {
                mMovieView.showControls();
            }
        }
    }

    /**
     * Enters Picture-in-Picture mode.
     */
    void minimize() {
        if (mMovieView == null) {
            return;
        }
        // Hide the controls in picture-in-picture mode.
        mMovieView.hideControls();
        // Calculate the aspect ratio of the PiP screen.
        Rational aspectRatio = new Rational(mMovieView.getWidth(), mMovieView.getHeight());
        mPictureInPictureParamsBuilder.setAspectRatio(aspectRatio).build();
        enterPictureInPictureMode(mPictureInPictureParamsBuilder.build());
    }

    /**
     * Adjusts immersive full-screen flags depending on the screen orientation.
     *
     * @param config The current {@link Configuration}.
     */
    private void adjustFullScreen(Configuration config) {
        final WindowInsetsControllerCompat insetsController =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
            mScrollView.setVisibility(View.GONE);
            mMovieView.setAdjustViewBounds(false);
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars());
            mScrollView.setVisibility(View.VISIBLE);
            mMovieView.setAdjustViewBounds(true);
        }
    }

    /**
     * Overloaded method that persists previously set media actions.
     *
     * @param state    The state of the video, e.g. playing, paused, etc.
     * @param position The position of playback in the video.
     * @param mediaId  The media id related to the video in the media session.
     */
    private void updatePlaybackState(
            @PlaybackStateCompat.State int state, int position, int mediaId) {
        long actions = mSession.getController().getPlaybackState().getActions();
        updatePlaybackState(state, actions, position, mediaId);
    }

    private void updatePlaybackState(
            @PlaybackStateCompat.State int state, long playbackActions, int position, int mediaId) {
        PlaybackStateCompat.Builder builder =
                new PlaybackStateCompat.Builder()
                        .setActions(playbackActions)
                        .setActiveQueueItemId(mediaId)
                        .setState(state, position, 1.0f);
        mSession.setPlaybackState(builder.build());
    }

    /**
     * Updates the {@link MovieView} based on the callback actions. <br>
     * Simulates a playlist that will disable actions when you cannot skip through the playlist in a
     * certain direction.
     */
    private class MediaSessionCallback extends MediaSessionCompat.Callback {

        private static final int PLAYLIST_SIZE = 2;

        private MovieView movieView;
        private int indexInPlaylist;

        public MediaSessionCallback(MovieView movieView) {
            this.movieView = movieView;
            indexInPlaylist = 1;
        }

        @Override
        public void onPlay() {
            super.onPlay();
            movieView.play();
        }

        @Override
        public void onPause() {
            super.onPause();
            movieView.pause();
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            movieView.startVideo();
            if (indexInPlaylist < PLAYLIST_SIZE) {
                indexInPlaylist++;
                if (indexInPlaylist >= PLAYLIST_SIZE) {
                    updatePlaybackState(
                            PlaybackStateCompat.STATE_PLAYING,
                            MEDIA_ACTIONS_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                            movieView.getCurrentPosition(),
                            movieView.getVideoResourceId());
                } else {
                    updatePlaybackState(
                            PlaybackStateCompat.STATE_PLAYING,
                            MEDIA_ACTIONS_ALL,
                            movieView.getCurrentPosition(),
                            movieView.getVideoResourceId());
                }
            }
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            movieView.startVideo();
            if (indexInPlaylist > 0) {
                indexInPlaylist--;
                if (indexInPlaylist <= 0) {
                    updatePlaybackState(
                            PlaybackStateCompat.STATE_PLAYING,
                            MEDIA_ACTIONS_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
                            movieView.getCurrentPosition(),
                            movieView.getVideoResourceId());
                } else {
                    updatePlaybackState(
                            PlaybackStateCompat.STATE_PLAYING,
                            MEDIA_ACTIONS_ALL,
                            movieView.getCurrentPosition(),
                            movieView.getVideoResourceId());
                }
            }
        }
    }

    private class SwitchActivityOnClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            startActivity(new Intent(view.getContext(), MainActivity.class));
            finish();
        }
    }
}
