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

package com.example.android.pictureinpicture.widget

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Handler
import android.os.Message
import android.support.annotation.RawRes
import android.transition.TransitionManager
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageButton
import android.widget.RelativeLayout
import com.example.android.pictureinpicture.R
import java.io.IOException
import java.lang.ref.WeakReference


/**
 * Provides video playback. There is nothing directly related to Picture-in-Picture here.

 *
 * This is similar to [android.widget.VideoView], but it comes with a custom control
 * (play/pause, fast forward, and fast rewind).
 */
class MovieView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                          defStyleAttr: Int = 0) :
        RelativeLayout(context, attrs, defStyleAttr) {

    companion object {

        private val TAG = "MovieView"

        /** The amount of time we are stepping forward or backward for fast-forward and fast-rewind.  */
        private val FAST_FORWARD_REWIND_INTERVAL = 5000 // ms

        /** The amount of time until we fade out the controls.  */
        private val TIMEOUT_CONTROLS = 3000L // ms

    }

    /**
     * Monitors all events related to [MovieView].
     */
    abstract class MovieListener {

        /**
         * Called when the video is started or resumed.
         */
        open fun onMovieStarted() {}

        /**
         * Called when the video is paused or finished.
         */
        open fun onMovieStopped() {}

        /**
         * Called when this view should be minimized.
         */
        open fun onMovieMinimized() {}
    }

    /** Shows the video playback.  */
    private val mSurfaceView: SurfaceView

    // Controls
    private val mToggle: ImageButton
    private val mShade: View
    private val mFastForward: ImageButton
    private val mFastRewind: ImageButton
    private val mMinimize: ImageButton

    /** This plays the video. This will be null when no video is set.  */
    internal var mMediaPlayer: MediaPlayer? = null

    /** The resource ID for the video to play.  */
    @RawRes
    private var mVideoResourceId: Int = 0

    var title: String = ""

    /** Whether we adjust our view bounds or we fill the remaining area with black bars  */
    private var mAdjustViewBounds: Boolean = false

    /** Handles timeout for media controls.  */
    private var mTimeoutHandler: TimeoutHandler? = null

    /** The listener for all the events we publish.  */
    internal var mMovieListener: MovieListener? = null

    private var mSavedCurrentPosition: Int = 0

    init {
        setBackgroundColor(Color.BLACK)

        // Inflate the content
        View.inflate(context, R.layout.view_movie, this)
        mSurfaceView = findViewById<SurfaceView>(R.id.surface)
        mShade = findViewById<View>(R.id.shade)
        mToggle = findViewById<ImageButton>(R.id.toggle)
        mFastForward = findViewById<ImageButton>(R.id.fast_forward)
        mFastRewind = findViewById<ImageButton>(R.id.fast_rewind)
        mMinimize = findViewById<ImageButton>(R.id.minimize)

        // Attributes
        val a = context.obtainStyledAttributes(attrs, R.styleable.MovieView,
                defStyleAttr, R.style.Widget_PictureInPicture_MovieView)
        setVideoResourceId(a.getResourceId(R.styleable.MovieView_android_src, 0))
        setAdjustViewBounds(a.getBoolean(R.styleable.MovieView_android_adjustViewBounds, false))
        title = a.getString(R.styleable.MovieView_android_title)
        a.recycle()

        // Bind view events
        val listener = View.OnClickListener { view ->
            when (view.id) {
                R.id.surface -> toggleControls()
                R.id.toggle -> toggle()
                R.id.fast_forward -> fastForward()
                R.id.fast_rewind -> fastRewind()
                R.id.minimize -> mMovieListener?.onMovieMinimized()
            }
            // Start or reset the timeout to hide controls
            mMediaPlayer?.let { player ->
                if (mTimeoutHandler == null) {
                    mTimeoutHandler = TimeoutHandler(this@MovieView)
                }
                mTimeoutHandler?.let { handler ->
                    handler.removeMessages(TimeoutHandler.MESSAGE_HIDE_CONTROLS)
                    if (player.isPlaying) {
                        handler.sendEmptyMessageDelayed(TimeoutHandler.MESSAGE_HIDE_CONTROLS,
                                TIMEOUT_CONTROLS)
                    }
                }
            }
        }
        mSurfaceView.setOnClickListener(listener)
        mToggle.setOnClickListener(listener)
        mFastForward.setOnClickListener(listener)
        mFastRewind.setOnClickListener(listener)
        mMinimize.setOnClickListener(listener)

        // Prepare video playback
        mSurfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                openVideo(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int,
                                        width: Int, height: Int) {
                // Do nothing
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                mMediaPlayer?.let { mSavedCurrentPosition = it.currentPosition }
                closeVideo()
            }
        })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mMediaPlayer?.let { player ->
            val videoWidth = player.videoWidth
            val videoHeight = player.videoHeight
            if (videoWidth != 0 && videoHeight != 0) {
                val aspectRatio = videoHeight.toFloat() / videoWidth
                val width = View.MeasureSpec.getSize(widthMeasureSpec)
                val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
                val height = View.MeasureSpec.getSize(heightMeasureSpec)
                val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
                if (mAdjustViewBounds) {
                    if (widthMode == View.MeasureSpec.EXACTLY
                            && heightMode != View.MeasureSpec.EXACTLY) {
                        super.onMeasure(widthMeasureSpec,
                                View.MeasureSpec.makeMeasureSpec((width * aspectRatio).toInt(),
                                        View.MeasureSpec.EXACTLY))
                    } else if (widthMode != View.MeasureSpec.EXACTLY
                            && heightMode == View.MeasureSpec.EXACTLY) {
                        super.onMeasure(View.MeasureSpec.makeMeasureSpec((height / aspectRatio).toInt(),
                                View.MeasureSpec.EXACTLY), heightMeasureSpec)
                    } else {
                        super.onMeasure(widthMeasureSpec,
                                View.MeasureSpec.makeMeasureSpec((width * aspectRatio).toInt(),
                                        View.MeasureSpec.EXACTLY))
                    }
                } else {
                    val viewRatio = height.toFloat() / width
                    if (aspectRatio > viewRatio) {
                        val padding = ((width - height / aspectRatio) / 2).toInt()
                        setPadding(padding, 0, padding, 0)
                    } else {
                        val padding = ((height - width * aspectRatio) / 2).toInt()
                        setPadding(0, padding, 0, padding)
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
                return
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDetachedFromWindow() {
        mTimeoutHandler?.removeMessages(TimeoutHandler.MESSAGE_HIDE_CONTROLS)
        mTimeoutHandler = null
        super.onDetachedFromWindow()
    }

    /**
     * The raw resource id of the video to play.

     * @return ID of the video resource.
     */
    fun getVideoResourceId(): Int = mVideoResourceId

    /**
     * Sets the listener to monitor movie events.

     * @param movieListener The listener to be set.
     */
    fun setMovieListener(movieListener: MovieListener?) {
        mMovieListener = movieListener
    }

    /**
     * Sets the raw resource ID of video to play.

     * @param id The raw resource ID.
     */
    fun setVideoResourceId(@RawRes id: Int) {
        if (id == mVideoResourceId) {
            return
        }
        mVideoResourceId = id
        val surface = mSurfaceView.holder.surface
        if (surface != null && surface.isValid) {
            closeVideo()
            openVideo(surface)
        }
    }

    fun setAdjustViewBounds(adjustViewBounds: Boolean) {
        if (mAdjustViewBounds == adjustViewBounds) {
            return
        }
        mAdjustViewBounds = adjustViewBounds
        if (adjustViewBounds) {
            background = null
        } else {
            setBackgroundColor(Color.BLACK)
        }
        requestLayout()
    }

    /**
     * Shows all the controls.
     */
    fun showControls() {
        TransitionManager.beginDelayedTransition(this)
        mShade.visibility = View.VISIBLE
        mToggle.visibility = View.VISIBLE
        mFastForward.visibility = View.VISIBLE
        mFastRewind.visibility = View.VISIBLE
        mMinimize.visibility = View.VISIBLE
    }

    /**
     * Hides all the controls.
     */
    fun hideControls() {
        TransitionManager.beginDelayedTransition(this)
        mShade.visibility = View.INVISIBLE
        mToggle.visibility = View.INVISIBLE
        mFastForward.visibility = View.INVISIBLE
        mFastRewind.visibility = View.INVISIBLE
        mMinimize.visibility = View.INVISIBLE
    }

    /**
     * Fast-forward the video.
     */
    fun fastForward() {
        mMediaPlayer?.let { it.seekTo(it.currentPosition + FAST_FORWARD_REWIND_INTERVAL) }
    }

    /**
     * Fast-rewind the video.
     */
    fun fastRewind() {
        mMediaPlayer?.let { it.seekTo(it.currentPosition - FAST_FORWARD_REWIND_INTERVAL) }
    }

    /**
     * Returns the current position of the video. If the the player has not been created, then
     * assumes the beginning of the video.

     * @return The current position of the video.
     */
    fun getCurrentPosition(): Int = mMediaPlayer?.currentPosition ?: 0

    val isPlaying: Boolean
        get() = mMediaPlayer?.isPlaying ?: false

    fun play() {
        if (mMediaPlayer == null) {
            return
        }
        mMediaPlayer!!.start()
        adjustToggleState()
        keepScreenOn = true
        mMovieListener?.onMovieStarted()
    }

    fun pause() {
        if (mMediaPlayer == null) {
            adjustToggleState()
            return
        }
        mMediaPlayer!!.pause()
        adjustToggleState()
        keepScreenOn = false
        mMovieListener?.onMovieStopped()
    }

    internal fun openVideo(surface: Surface) {
        if (mVideoResourceId == 0) {
            return
        }
        mMediaPlayer = MediaPlayer()
        mMediaPlayer?.let { player ->
            player.setSurface(surface)
            startVideo()
        }
    }

    /**
     * Restarts playback of the video.
     */
    public fun startVideo() {
        mMediaPlayer?.let { player ->
            player.reset()
            try {
                resources.openRawResourceFd(mVideoResourceId).use { fd ->
                    player.setDataSource(fd)
                    player.setOnPreparedListener { mediaPlayer ->
                        // Adjust the aspect ratio of this view
                        requestLayout()
                        if (mSavedCurrentPosition > 0) {
                            mediaPlayer.seekTo(mSavedCurrentPosition)
                            mSavedCurrentPosition = 0
                        } else {
                            // Start automatically
                            play()
                        }
                    }
                    player.setOnCompletionListener {
                        adjustToggleState()
                        keepScreenOn = false
                        mMovieListener?.onMovieStopped()
                    }
                    player.prepare()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to open video", e)
            }
        }
    }

    internal fun closeVideo() {
        mMediaPlayer?.release()
        mMediaPlayer = null
    }

    internal fun toggle() {
        mMediaPlayer?.let { if (it.isPlaying) pause() else play() }
    }

    internal fun toggleControls() {
        if (mShade.visibility == View.VISIBLE) {
            hideControls()
        } else {
            showControls()
        }
    }

    internal fun adjustToggleState() {
        mMediaPlayer?.let {
            if (it.isPlaying) {
                mToggle.contentDescription = resources.getString(R.string.pause)
                mToggle.setImageResource(R.drawable.ic_pause_64dp)
            } else {
                mToggle.contentDescription = resources.getString(R.string.play)
                mToggle.setImageResource(R.drawable.ic_play_arrow_64dp)
            }
        }
    }

    private class TimeoutHandler(view: MovieView) : Handler() {

        private val mMovieViewRef: WeakReference<MovieView> = WeakReference(view)

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_HIDE_CONTROLS -> {
                    mMovieViewRef.get()?.hideControls()
                }
                else -> super.handleMessage(msg)
            }
        }

        companion object {
            internal val MESSAGE_HIDE_CONTROLS = 1
        }

    }

}
