package dev.padbridge.app;

import android.app.Activity;
import android.widget.LinearLayout;

/** SDK-free distribution: the bridge has no advertising dependency. */
public final class AdBanner {
    public AdBanner(Activity activity) {}
    public void attach(LinearLayout host) {}
    public void pause() {}
    public void destroy() {}
    public static boolean isDemo() { return false; }
    public static String privacySummary() {
        return "This build contains no advertising SDK and does not load ads. Profiles and diagnostics stay on your phone. The app does not send them to external servers; you can share a report manually.";
    }
}
