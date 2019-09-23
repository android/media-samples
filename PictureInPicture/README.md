
Android PictureInPicture Sample
===============================

This sample demonstrates basic usage of Picture-in-Picture mode for handheld devices.
The sample plays a video. The video keeps on playing when the app is turned in to
Picture-in-Picture mode. On Picture-in-Picture screen, the app shows an action item to
pause or resume the video.

Introduction
------------

As of Android O, activities can launch in [Picture-in-Picture (PiP)][1] mode. PiP is a
special type of [multi-window][2] mode mostly used for video playback.

The app is *paused* when it enters PiP mode, but it should continue showing content. For this
reason, you should make sure your app does not pause playback in its [onPause()][3]
handler. Instead, you should pause video in [onStop()][4]. For more information, see [Multi-Window
Lifecycle][5].

To specify that your activity can use PIP mode, set `android:supportsPictureInPicture` to `true` in
the manifest. (Beginning with Android O, you do not need to set
`android:resizeableActivity` to `true` if you are supporting PIP mode you only need to
`setrandroid:resizeableActivity` if your activity supports other multi-window modes.)

You can pass a [PictureInPictureParams][6] to [enterPictureInPictureMode()][7] to specify how an
activity should behave when it is in PiP mode. You can also use it to call
[setPictureInPictureParams()][8] and update the current behavior. If the app is in not PiP mode, it
will be used for later call of [enterPictureInPictureMode()][7].

With a [PictureInPictureParams][6], you can specify aspect ratio of PiP activity and action items
available for PiP mode. The aspect ratio is used when the activity is in PiP mode. The action items
are used as menu items in PiP mode. You can use a [PendingIntent][9] to specify what to do when the
item is selected.

[1]: https://developer.android.com/guide/topics/ui/picture-in-picture.html
[2]: https://developer.android.com/guide/topics/ui/multi-window.html
[3]: https://developer.android.com/reference/android/app/Activity.html#onPause()
[4]: https://developer.android.com/reference/android/app/Activity.html#onStop()
[5]: https://developer.android.com/guide/topics/ui/multi-window.html#lifecycle
[6]: https://developer.android.com/reference/android/app/PictureInPictureParams.html
[7]: https://developer.android.com/reference/android/app/Activity.html#enterPictureInPictureMode(android.app.PictureInPictureParams)
[8]: https://developer.android.com/reference/android/app/Activity.html#setPictureInPictureParams(android.app.PictureInPictureParams)
[9]: https://developer.android.com/reference/android/app/PendingIntent.html

Pre-requisites
--------------

- Android SDK 28
- Android Build Tools v28.0.3
- Android Support Repository

Screenshots
-------------

<img src="screenshots/1-main.png" height="400" alt="Screenshot"/> <img src="screenshots/2-pip.png" height="400" alt="Screenshot"/> 

Getting Started
---------------

This sample uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Support
-------

- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/android/media

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.
