Android Video Player Sample
===========================

This sample shows how to implement a media app that allows
playback of video from local storage (assets folder in the APK)
or remote sources over HTTP(S).
1. It supports playlists, so that multiple videos can be strung
together to play one after the other, and skip between them.
2. It supports `MediaSession` so that external Bluetooth headphones
can control your media (play, pause, skip to next, etc), and see
what media is currently playing (like from a car's Bluetooth head
unit).
3. It supports Audio Focus, so that you can respect Android's 
audio focus system and pause playback if something else is playing.
4. It supports picture in picture (PIP) so that the app's video
playback can continue in a minimized window while the user is
in other apps.
To learn more about `ExoPlayer`, `MediaSession`, Audio Focus, and PIP, 
please read this series of [articles on Medium](https://goo.gl/HpTnka)
that goes into the details of these APIs.

Pre-requisites
--------------

- Android SDK 26
- Android Build Tools v26.0.1
- Android Support Repository

Getting Started
---------------

This sample uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Support
-------

- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue
on GitHub.

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.
