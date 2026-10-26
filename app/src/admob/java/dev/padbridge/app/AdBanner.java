package dev.padbridge.app;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize;
import com.google.android.libraries.ads.mobile.sdk.banner.AdView;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Opt-in Google test banner. Never referenced by the privileged bridge. */
public final class AdBanner {
    private static final String APP_ID = "ca-app-pub-3940256099942544~3347511713";
    private static final String BANNER_ID = "ca-app-pub-3940256099942544/9214589741";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Accessed on the main thread. Initialization belongs to the app process.
    private static boolean initialized;
    private static boolean initializing;
    private static long initializationGeneration;
    private static final List<Pending> PENDING = new ArrayList<>();

    private final Activity activity;
    private LinearLayout card;
    private FrameLayout slot;
    private TextView status;
    private Button loadButton;
    private Button hideButton;
    private AdView banner;
    private boolean visible;
    private boolean destroyed;
    private boolean requested;
    private long generation;

    public AdBanner(Activity activity) { this.activity = activity; }

    public static boolean isDemo() { return true; }

    public static String privacySummary() {
        return "Google test ads only; no revenue. Tapping Load test banner lets the Google SDK connect to the network: Google receives your IP address and technical device data. This build does not request advertising ID or location. Hiding the banner stops its display but does not guarantee that all SDK background activity stops. To fully stop the app, use Force stop in Android app settings. This is a demonstration; consent management for real monetization has not been configured.";
    }

    public void attach(LinearLayout host) {
        if (destroyed || !alive()) return;
        visible = true;
        if (card == null) {
            card = Ui.card(host);
            Ui.add(card, "TEST ADVERTISING", 13, Ui.WARN);
            Ui.add(card, privacySummary(), 13, Ui.MUTED);
            status = Ui.add(card, "The SDK starts only after you tap the button.", 13, Ui.TEXT);
            if (initialized || initializing) status.setText("The SDK was already started in this app process. A new banner loads only when tapped.");
            slot = new FrameLayout(activity);
            card.addView(slot, new LinearLayout.LayoutParams(-1, -2));
            loadButton = Ui.button(card, "Load test banner",
                    false, this::requestDemo);
            hideButton = Ui.button(card, "Hide banner", false, this::hideDemo);
            hideButton.setVisibility(View.GONE);
        } else if (card.getParent() != host) {
            if (card.getParent() instanceof ViewGroup) ((ViewGroup) card.getParent()).removeView(card);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = Ui.dp(activity, 16);
            host.addView(card, params);
        }
    }

    private boolean alive() { return !activity.isFinishing() && !activity.isDestroyed(); }
    private boolean current(long ticket) {
        return !destroyed && visible && requested && generation == ticket && alive()
                && card != null && card.getParent() != null;
    }

    private void requestDemo() {
        if (destroyed || !visible || !alive() || requested) return;
        // No release variant exists; keep a second runtime boundary for test IDs.
        if ((activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            status.setText("Test advertising is available only in the debug build.");
            return;
        }
        requested = true;
        final long ticket = ++generation;
        loadButton.setEnabled(false);
        hideButton.setVisibility(View.VISIBLE);
        status.setText("Connecting to Google for test ads…");
        if (initialized) { loadBanner(ticket); return; }
        PENDING.add(new Pending(this, ticket));
        if (initializing) return;
        initializing = true;
        final long initializationTicket = ++initializationGeneration;
        Context application = activity.getApplicationContext();
        new Thread(() -> {
            try {
                // Next-Gen initialization is explicit and must run off the UI thread.
                MobileAds.initialize(application, new InitializationConfig.Builder(APP_ID).build(),
                        result -> MAIN.post(() -> completeInitialization(initializationTicket, true)));
            } catch (RuntimeException | LinkageError error) {
                MAIN.post(() -> completeInitialization(initializationTicket, false));
            }
        }, "PadBridge-AdMobDemo").start();
    }

    private static void completeInitialization(long ticket, boolean success) {
        if (!initializing || ticket != initializationGeneration) return;
        initializing = false;
        initialized = success;
        List<Pending> waiting = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending pending : waiting) {
            AdBanner owner = pending.owner.get();
            if (owner == null || !owner.current(pending.ticket)) continue;
            if (success) owner.loadBanner(pending.ticket);
            else owner.failed(pending.ticket);
        }
    }

    private void loadBanner(long ticket) {
        if (!current(ticket)) return;
        slot.post(() -> {
            if (!current(ticket)) return;
            int available = slot.getWidth() - slot.getPaddingLeft() - slot.getPaddingRight();
            int widthDp = (int) (available / activity.getResources().getDisplayMetrics().density);
            if (widthDp < 1) { failed(ticket); return; }
            try {
                AdView view = new AdView(activity);
                banner = view;
                slot.addView(view, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
                AdSize size = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp);
                BannerAdRequest request = new BannerAdRequest.Builder(BANNER_ID, size).build();
                view.loadAd(request, new AdLoadCallback<BannerAd>() {
                    @Override public void onAdLoaded(BannerAd ad) {
                        MAIN.post(() -> {
                            if (!current(ticket) || banner != view) { releaseView(view); return; }
                            status.setText("Google test banner. No revenue.");
                        });
                    }
                    @Override public void onAdFailedToLoad(LoadAdError error) {
                        MAIN.post(() -> { if (current(ticket) && banner == view) failed(ticket); });
                    }
                });
            } catch (RuntimeException | LinkageError error) { failed(ticket); }
        });
    }

    private void failed(long ticket) {
        if (!current(ticket)) return;
        hideDemo();
        if (status != null) status.setText("The test banner could not load. Gamepad control continues to work.");
    }

    private void hideDemo() {
        generation++;
        requested = false;
        destroyBanner();
        if (loadButton != null) loadButton.setEnabled(true);
        if (hideButton != null) hideButton.setVisibility(View.GONE);
        if (status != null) status.setText("Banner hidden. The SDK may continue background activity until the app is force stopped.");
    }

    private void destroyBanner() {
        if (banner == null) return;
        AdView old = banner;
        banner = null;
        if (old.getParent() instanceof ViewGroup) ((ViewGroup) old.getParent()).removeView(old);
        releaseView(old);
    }

    private static void releaseView(AdView view) {
        try { view.destroy(); } catch (RuntimeException | LinkageError ignored) {
            // A failed SDK cleanup must not stop controller translation.
        }
    }

    public void pause() {
        visible = false;
        hideDemo();
        if (card != null && card.getParent() instanceof ViewGroup)
            ((ViewGroup) card.getParent()).removeView(card);
        card = null;
        slot = null;
        status = null;
        loadButton = null;
        hideButton = null;
    }

    public void destroy() { pause(); destroyed = true; }

    private static final class Pending {
        final WeakReference<AdBanner> owner;
        final long ticket;
        Pending(AdBanner owner, long ticket) { this.owner = new WeakReference<>(owner); this.ticket = ticket; }
    }
}
