# Keep JavaScript bridge methods used by WebView.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
