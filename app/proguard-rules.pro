-repackageclasses 'a'
-allowaccessmodification
-keepattributes *Annotation*

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

-keep class com.talksy.messenger.data.model.** { *; }
