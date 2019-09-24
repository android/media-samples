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

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.app.AppCompatActivity
import android.util.Rational
import android.view.View
import android.widget.Button
import android.widget.ScrollView

import com.example.android.pictureinpicture.widget.MovieView


/**
 * Demonstrates usage of Picture-in-Picture when using
 * [android.support.v4.media.session.MediaSessionCompat].
 */
class MediaSessionPlaybackActivity : AppCompatActivity() {

    companion object {

        private val TAG = "MediaSessionPlaybackActivity"

        val MEDIA_ACTIONS_PLAY_PAUSE =
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE

        val MEDIA_ACTIONS_ALL =
                MEDIA_ACTIONS_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    }

    private lateinit var mSession: MediaSessionCompat

    /** The arguments to be used for Picture-in-Picture mode.  */
    private val mPictureInPictureParamsBuilder = PictureInPictureParams.Builder()

    /** This shows the video.  */
    private lateinit var mMovieView: MovieView

    /** The bottom half of the screen; hidden on landscape  */
    private lateinit var mScrollView: ScrollView

    private val mOnClickListener = View.OnClickListener { view ->
        when (view.id) {
            R.id.pip -> minimize()
        }
    }

    /**
     * Callbacks from the [MovieView] showing the video playback.
     */
    private val mMovieListener = object : MovieView.MovieListener() {

        override fun onMovieStarted() {
            // We are playing the video now. Update the media session state and the PiP window will
            // update the actions.
            mMovieView?.let { view ->
                updatePlaybackState(
                        PlaybackStateCompat.STATE_PLAYING,
                        view.getCurrentPosition(),
                        view.getVideoResourceId())
            }
        }

        override fun onMovieStopped() {
            // The video stopped or reached its end. Update the media session state and the PiP window will
            // update the actions.
            mMovieView?.let { view ->
                updatePlaybackState(
                        PlaybackStateCompat.STATE_PAUSED,
                        view.getCurrentPosition(),
                        view.getVideoResourceId())
            }
        }

        override fun onMovieMinimized() {
            // The MovieView wants us to minimize it. We enter Picture-in-Picture mode now.
            minimize()
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View references
        mMovieView = findViewById<MovieView>(R.id.movie)
        mScrollView = findViewById<ScrollView>(R.id.scroll)
        val switchExampleButton = findViewById<Button>(R.id.switch_example)
        switchExampleButton.text = getString(R.string.switch_custom)
        switchExampleButton.setOnClickListener(SwitchActivityOnClick())

        // Set up the video; it automatically starts.
        mMovieView.setMovieListener(mMovieListener)
        findViewById<View>(R.id.pip).setOnClickListener(mOnClickListener)
    }

    override fun onStart() {
        super.onStart()
        initializeMediaSession()
    }

    private fun initializeMediaSession() {
        mSession = MediaSessionCompat(this, TAG)
        mSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mSession.isActive = true
        MediaControllerCompat.setMediaController(this, mSession.controller)

        val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, mMovieView.title)
                .build()
        mSession.setMetadata(metadata)

        val mMediaSessionCallback = MediaSessionCallback(mMovieView)
        mSession.setCallback(mMediaSessionCallback)

        val state = if (mMovieView.isPlaying)
            PlaybackStateCompat.STATE_PLAYING
        else
            PlaybackStateCompat.STATE_PAUSED
        updatePlaybackState(
                state,
                MEDIA_ACTIONS_ALL,
                mMovieView.getCurrentPosition(),
                mMovieView.getVideoResourceId())
    }

    override fun onStop() {
        super.onStop()
        // On entering Picture-in-Picture mode, onPause is called, but not onStop.
        // For this reason, this is the place where we should pause the video playback.
        mMovieView.pause()
        mSession.release()
    }

    override fun onRestart() {
        super.onRestart()
        if (!isInPictureInPictureMode) {
            // Show the video controls so the video can be easily resumed.
            mMovieView.showControls()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adjustFullScreen(newConfig)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            adjustFullScreen(resources.configuration)
        }
    }

    override fun onPictureInPictureModeChanged(
            isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            // Show the video controls if the video is not playing
            if (!mMovieView.isPlaying) {
                mMovieView.showControls()
            }
        }
    }

    /**
     * Enters Picture-in-Picture mode.
     */
    internal fun minimize() {
        // Hide the controls in picture-in-picture mode.
        mMovieView.hideControls()
        // Calculate the aspect ratio of the PiP screen.
        val aspectRatio = Rational(mMovieView.width, mMovieView.height)
        mPictureInPictureParamsBuilder.setAspectRatio(aspectRatio).build()
        enterPictureInPictureMode(mPictureInPictureParamsBuilder.build())
    }

    /**
     * Adjusts immersive full-screen flags depending on the screen orientation.

     * @param config The current [Configuration].
     */
    private fun adjustFullScreen(config: Configuration) {
        val decorView = window.decorView
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            mScrollView.visibility = View.GONE
            mMovieView.setAdjustViewBounds(false)
        } else {
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            mScrollView.visibility = View.VISIBLE
            mMovieView.setAdjustViewBounds(true)
        }
    }

    /**
     * Overloaded method that persists previously set media actions.

     * @param state The state of the video, e.g. playing, paused, etc.
     * *
     * @param position The position of playback in the video.
     * *
     * @param mediaId The media id related to the video in the media session.
     */
    private fun updatePlaybackState(
            @PlaybackStateCompat.State state: Int,
            position: Int,
            mediaId: Int) {
        val actions = mSession.controller.playbackState.actions
        updatePlaybackState(state, actions, position, mediaId)
    }

    private fun updatePlaybackState(
            @PlaybackStateCompat.State state: Int,
            playbackActions: Long,
            position: Int,
            mediaId: Int) {
        val builder = PlaybackStateCompat.Builder()
                .setActions(playbackActions)
                .setActiveQueueItemId(mediaId.toLong())
                .setState(state, position.toLong(), 1.0f)
        mSession.setPlaybackState(builder.build())
    }

    /**
     * Updates the [MovieView] based on the callback actions. <br></br>
     * Simulates a playlist that will disable actions when you cannot skip through the playlist in a
     * certain direction.
     */
    private inner class MediaSessionCallback(private val movieView: MovieView) : MediaSessionCompat.Callback() {
        private val PLAYLIST_SIZE = 2

        private var indexInPlaylist: Int = 0

        init {
            indexInPlaylist = 1
        }

        override fun onPlay() {
            super.onPlay()
            movieView.play()
        }

        override fun onPause() {
            super.onPause()
            movieView.pause()
        }

        override fun onSkipToNext() {
            super.onSkipToNext()
            movieView.startVideo()
            if( indexInPlaylist < PLAYLIST_SIZE ) {
                indexInPlaylist++
                if (indexInPlaylist >= PLAYLIST_SIZE) {
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING,
                            MEDIA_ACTIONS_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                            movieView.getCurrentPosition(),
                            movieView.getVideoResourceId())
                } else {
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING,
                            MEDIA_ACTIONS_ALL,
                            movieView.getCurrentPosition(),
                            movieView.getVideoResourceId())
                }
            }
        }

        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
            movieView.startVideo()
            if( indexInPlaylist > 0 ) {
                indexInPlaylist--
                if (indexInPlaylist <= 0) {
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING,
                            MEDIA_ACTIONS_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
                            movieView.getCurrentPosition(),
                            movieView.getVideoResourceId())
                } else {
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING,
                            MEDIA_ACTIONS_ALL,
                            movieView.getCurrentPosition(),
                            movieView.getVideoResourceId())
                }
            }
        }
    }

    private inner class SwitchActivityOnClick : View.OnClickListener {
        override fun onClick(view: View) {
            startActivity(Intent(view.context, MainActivity::class.java))
            finish()
        }
    }
}
