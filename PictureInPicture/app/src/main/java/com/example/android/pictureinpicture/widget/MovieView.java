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

package com.example.android.pictureinpicture.widget;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import com.example.android.pictureinpicture.R;

import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Provides video playback. There is nothing directly related to Picture-in-Picture here.
 *
 * <p>This is similar to {@link android.widget.VideoView}, but it comes with a custom control
 * (play/pause, fast forward, and fast rewind).
 */
public class MovieView extends RelativeLayout {

    /**
     * Monitors all events related to {@link MovieView}.
     */
    public abstract static class MovieListener {

        /**
         * Called when the video is started or resumed.
         */
        public void onMovieStarted() {
        }

        /**
         * Called when the video is paused or finished.
         */
        public void onMovieStopped() {
        }

        /**
         * Called when this view should be minimized.
         */
        public void onMovieMinimized() {
        }
    }

    private static final String TAG = "MovieView";

    /**
     * The amount of time we are stepping forward or backward for fast-forward and fast-rewind.
     */
    private static final int FAST_FORWARD_REWIND_INTERVAL = 5000; // ms

    /**
     * The amount of time until we fade out the controls.
     */
    private static final int TIMEOUT_CONTROLS = 3000; // ms

    /**
     * Shows the video playback.
     */
    private final SurfaceView mSurfaceView;

    // Controls
    private final ImageButton mToggle;
    private final View mShade;
    private final ImageButton mFastForward;
    private final ImageButton mFastRewind;
    private final ImageButton mMinimize;

    /**
     * This plays the video. This will be null when no video is set.
     */
    MediaPlayer mMediaPlayer;

    /**
     * The resource ID for the video to play.
     */
    @RawRes
    private int mVideoResourceId;

    /**
     * The title of the video
     */
    private String mTitle;

    /**
     * Whether we adjust our view bounds or we fill the remaining area with black bars
     */
    private boolean mAdjustViewBounds;

    /**
     * Handles timeout for media controls.
     */
    TimeoutHandler mTimeoutHandler;

    /**
     * The listener for all the events we publish.
     */
    MovieListener mMovieListener;

    private int mSavedCurrentPosition;

    public MovieView(Context context) {
        this(context, null);
    }

    public MovieView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MovieView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackgroundColor(Color.BLACK);

        // Inflate the content
        inflate(context, R.layout.view_movie, this);
        mSurfaceView = findViewById(R.id.surface);
        mShade = findViewById(R.id.shade);
        mToggle = findViewById(R.id.toggle);
        mFastForward = findViewById(R.id.fast_forward);
        mFastRewind = findViewById(R.id.fast_rewind);
        mMinimize = findViewById(R.id.minimize);

        final TypedArray attributes =
                context.obtainStyledAttributes(
                        attrs,
                        R.styleable.MovieView,
                        defStyleAttr,
                        R.style.Widget_PictureInPicture_MovieView);
        setVideoResourceId(attributes.getResourceId(R.styleable.MovieView_android_src, 0));
        setAdjustViewBounds(
                attributes.getBoolean(R.styleable.MovieView_android_adjustViewBounds, false));
        setTitle(attributes.getString(R.styleable.MovieView_android_title));
        attributes.recycle();

        // Bind view events
        final OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                int id = view.getId();
                if (id == R.id.surface) {
                    toggleControls();
                } else if (id == R.id.toggle) {
                    toggle();
                } else if (id == R.id.fast_forward) {
                    fastForward();
                } else if (id == R.id.fast_rewind) {
                    fastRewind();
                } else if (id == R.id.minimize) {
                    if (mMovieListener != null) {
                        mMovieListener.onMovieMinimized();
                    }
                }
                // Start or reset the timeout to hide controls
                if (mMediaPlayer != null) {
                    if (mTimeoutHandler == null) {
                        mTimeoutHandler = new TimeoutHandler(MovieView.this);
                    }
                    mTimeoutHandler.removeMessages(TimeoutHandler.MESSAGE_HIDE_CONTROLS);
                    if (mMediaPlayer.isPlaying()) {
                        mTimeoutHandler.sendEmptyMessageDelayed(
                                TimeoutHandler.MESSAGE_HIDE_CONTROLS, TIMEOUT_CONTROLS);
                    }
                }
            }
        };
        mSurfaceView.setOnClickListener(listener);
        mToggle.setOnClickListener(listener);
        mFastForward.setOnClickListener(listener);
        mFastRewind.setOnClickListener(listener);
        mMinimize.setOnClickListener(listener);

        // Prepare video playback
        mSurfaceView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                openVideo(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(
                                    SurfaceHolder holder, int format, int width, int height) {
                                // Do nothing
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                if (mMediaPlayer != null) {
                                    mSavedCurrentPosition = mMediaPlayer.getCurrentPosition();
                                }
                                closeVideo();
                            }
                        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mMediaPlayer != null) {
            final int videoWidth = mMediaPlayer.getVideoWidth();
            final int videoHeight = mMediaPlayer.getVideoHeight();
            if (videoWidth != 0 && videoHeight != 0) {
                final float aspectRatio = (float) videoHeight / videoWidth;
                final int width = MeasureSpec.getSize(widthMeasureSpec);
                final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
                final int height = MeasureSpec.getSize(heightMeasureSpec);
                final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
                if (mAdjustViewBounds) {
                    if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                        super.onMeasure(
                                widthMeasureSpec,
                                MeasureSpec.makeMeasureSpec(
                                        (int) (width * aspectRatio), MeasureSpec.EXACTLY));
                    } else if (widthMode != MeasureSpec.EXACTLY
                            && heightMode == MeasureSpec.EXACTLY) {
                        super.onMeasure(
                                MeasureSpec.makeMeasureSpec(
                                        (int) (height / aspectRatio), MeasureSpec.EXACTLY),
                                heightMeasureSpec);
                    } else {
                        super.onMeasure(
                                widthMeasureSpec,
                                MeasureSpec.makeMeasureSpec(
                                        (int) (width * aspectRatio), MeasureSpec.EXACTLY));
                    }
                } else {
                    final float viewRatio = (float) height / width;
                    if (aspectRatio > viewRatio) {
                        int padding = (int) ((width - height / aspectRatio) / 2);
                        setPadding(padding, 0, padding, 0);
                    } else {
                        int padding = (int) ((height - width * aspectRatio) / 2);
                        setPadding(0, padding, 0, padding);
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
                return;
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mTimeoutHandler != null) {
            mTimeoutHandler.removeMessages(TimeoutHandler.MESSAGE_HIDE_CONTROLS);
            mTimeoutHandler = null;
        }
        super.onDetachedFromWindow();
    }

    /**
     * Sets the listener to monitor movie events.
     *
     * @param movieListener The listener to be set.
     */
    public void setMovieListener(@Nullable MovieListener movieListener) {
        mMovieListener = movieListener;
    }

    /**
     * Sets the title of the video to play.
     *
     * @param title of the video.
     */
    public void setTitle(String title) {
        this.mTitle = title;
    }

    /**
     * The title of the video to play.
     *
     * @return title of the video.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * The raw resource id of the video to play.
     *
     * @return ID of the video resource.
     */
    public int getVideoResourceId() {
        return mVideoResourceId;
    }

    /**
     * Sets the raw resource ID of video to play.
     *
     * @param id The raw resource ID.
     */
    public void setVideoResourceId(@RawRes int id) {
        if (id == mVideoResourceId) {
            return;
        }
        mVideoResourceId = id;
        Surface surface = mSurfaceView.getHolder().getSurface();
        if (surface != null && surface.isValid()) {
            closeVideo();
            openVideo(surface);
        }
    }

    public void setAdjustViewBounds(boolean adjustViewBounds) {
        if (mAdjustViewBounds == adjustViewBounds) {
            return;
        }
        mAdjustViewBounds = adjustViewBounds;
        if (adjustViewBounds) {
            setBackground(null);
        } else {
            setBackgroundColor(Color.BLACK);
        }
        requestLayout();
    }

    /**
     * Shows all the controls.
     */
    public void showControls() {
        TransitionManager.beginDelayedTransition(this);
        mShade.setVisibility(View.VISIBLE);
        mToggle.setVisibility(View.VISIBLE);
        mFastForward.setVisibility(View.VISIBLE);
        mFastRewind.setVisibility(View.VISIBLE);
        mMinimize.setVisibility(View.VISIBLE);
    }

    /**
     * Hides all the controls.
     */
    public void hideControls() {
        TransitionManager.beginDelayedTransition(this);
        mShade.setVisibility(View.INVISIBLE);
        mToggle.setVisibility(View.INVISIBLE);
        mFastForward.setVisibility(View.INVISIBLE);
        mFastRewind.setVisibility(View.INVISIBLE);
        mMinimize.setVisibility(View.INVISIBLE);
    }

    /**
     * Fast-forward the video.
     */
    public void fastForward() {
        if (mMediaPlayer == null) {
            return;
        }
        mMediaPlayer.seekTo(mMediaPlayer.getCurrentPosition() + FAST_FORWARD_REWIND_INTERVAL);
    }

    /**
     * Fast-rewind the video.
     */
    public void fastRewind() {
        if (mMediaPlayer == null) {
            return;
        }
        mMediaPlayer.seekTo(mMediaPlayer.getCurrentPosition() - FAST_FORWARD_REWIND_INTERVAL);
    }

    /**
     * Returns the current position of the video. If the the player has not been created, then
     * assumes the beginning of the video.
     *
     * @return The current position of the video.
     */
    public int getCurrentPosition() {
        if (mMediaPlayer == null) {
            return 0;
        }
        return mMediaPlayer.getCurrentPosition();
    }

    public boolean isPlaying() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    public void play() {
        if (mMediaPlayer == null) {
            return;
        }
        mMediaPlayer.start();
        adjustToggleState();
        setKeepScreenOn(true);
        if (mMovieListener != null) {
            mMovieListener.onMovieStarted();
        }
    }

    public void pause() {
        if (mMediaPlayer == null) {
            adjustToggleState();
            return;
        }
        mMediaPlayer.pause();
        adjustToggleState();
        setKeepScreenOn(false);
        if (mMovieListener != null) {
            mMovieListener.onMovieStopped();
        }
    }

    void openVideo(Surface surface) {
        if (mVideoResourceId == 0) {
            return;
        }
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setSurface(surface);
        startVideo();
    }

    /**
     * Restarts playback of the video.
     */
    public void startVideo() {
        mMediaPlayer.reset();
        try (AssetFileDescriptor fd = getResources().openRawResourceFd(mVideoResourceId)) {
            mMediaPlayer.setDataSource(fd);
            mMediaPlayer.setOnPreparedListener(
                    new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mediaPlayer) {
                            // Adjust the aspect ratio of this view
                            requestLayout();
                            if (mSavedCurrentPosition > 0) {
                                mediaPlayer.seekTo(mSavedCurrentPosition);
                                mSavedCurrentPosition = 0;
                            } else {
                                // Start automatically
                                play();
                            }
                        }
                    });
            mMediaPlayer.setOnCompletionListener(
                    new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            adjustToggleState();
                            setKeepScreenOn(false);
                            if (mMovieListener != null) {
                                mMovieListener.onMovieStopped();
                            }
                        }
                    });
            mMediaPlayer.prepare();
        } catch (IOException e) {
            Log.e(TAG, "Failed to open video", e);
        }
    }

    void closeVideo() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    void toggle() {
        if (mMediaPlayer == null) {
            return;
        }
        if (mMediaPlayer.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    void toggleControls() {
        if (mShade.getVisibility() == View.VISIBLE) {
            hideControls();
        } else {
            showControls();
        }
    }

    void adjustToggleState() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mToggle.setContentDescription(getResources().getString(R.string.pause));
            mToggle.setImageResource(R.drawable.ic_pause_64dp);
        } else {
            mToggle.setContentDescription(getResources().getString(R.string.play));
            mToggle.setImageResource(R.drawable.ic_play_arrow_64dp);
        }
    }

    private static class TimeoutHandler extends Handler {

        static final int MESSAGE_HIDE_CONTROLS = 1;

        private final WeakReference<MovieView> mMovieViewRef;

        TimeoutHandler(MovieView view) {
            super(Looper.getMainLooper());
            mMovieViewRef = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_HIDE_CONTROLS) {
                MovieView movieView = mMovieViewRef.get();
                if (movieView != null) {
                    movieView.hideControls();
                }
            } else {
                super.handleMessage(msg);
            }
        }
    }
}
