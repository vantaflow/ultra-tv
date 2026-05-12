package com.ultratv.tv;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

/**
 * Bridges Android TV / Fire TV / Google TV remote D-pad input into the WebView.
 *
 * Android delivers DPAD keycodes to the foreground Activity. By default the
 * WebView ignores them — so arrow keys on a remote never reach JavaScript and
 * spatial navigation appears broken.
 *
 * This Activity does three things:
 *   1. Logs every received keycode to logcat ("UltraTVKey") so issues are easy
 *      to diagnose with `adb logcat -s UltraTVKey`.
 *   2. Calls JS function `window.__ultratv_remote(action)` directly when it can,
 *      where action is one of "up","down","left","right","enter","back".
 *   3. As a belt-and-suspenders fallback, also dispatches a synthetic
 *      KeyboardEvent on `window`.
 *
 * The dual path means whichever of the two channels works on the user's TV
 * will move focus — we don't depend on a single API surface.
 */
public class MainActivity extends BridgeActivity {

    private static final String TAG = "UltraTVKey";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ExternalPlayerPlugin.class);
        super.onCreate(savedInstanceState);

        WebView wv = getWebView();
        if (wv != null) {
            wv.setFocusable(true);
            wv.setFocusableInTouchMode(true);
            wv.requestFocus();
            // Inject native-detected TV flag and device info as a JS bridge BEFORE
            // any page script runs. JS can read window.__ultratv_isTv synchronously
            // — no UA guessing, no race with React hydration.
            final boolean isTv = detectTelevision();
            final String model = android.os.Build.MODEL == null ? "" : android.os.Build.MODEL.replace("'", "");
            final String manuf = android.os.Build.MANUFACTURER == null ? "" : android.os.Build.MANUFACTURER.replace("'", "");
            final String js =
                "window.__ultratv_isTv=" + (isTv ? "true" : "false") + ";" +
                "window.__ultratv_device={model:'" + model + "',manufacturer:'" + manuf + "',sdk:" + android.os.Build.VERSION.SDK_INT + "};";
            wv.evaluateJavascript(js, null);
            Log.i(TAG, "Injected isTv=" + isTv + " model=" + model + " manuf=" + manuf);
        }
        Log.i(TAG, "MainActivity ready, WebView=" + (wv != null));
    }

    // Mirror StreamVault's robust TV detection — no UA guessing.
    private boolean detectTelevision() {
        try {
            PackageManager pm = getPackageManager();
            if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true;
            if (pm.hasSystemFeature("android.software.leanback_only")) return true;
            if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true;
            if (pm.hasSystemFeature("amazon.hardware.fire_tv")) return true;
            UiModeManager um = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            if (um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return true;
            // Last resort: no touchscreen + >=900dp screen → likely a TV box (Mecool, etc).
            boolean hasTouch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
            int widthDp = getResources().getConfiguration().screenWidthDp;
            if (!hasTouch && widthDp >= 900) return true;
        } catch (Throwable t) {
            Log.e(TAG, "detectTelevision failed: " + t);
        }
        return false;
    }

    private WebView getWebView() {
        try {
            return (WebView) getBridge().getWebView();
        } catch (Throwable t) {
            Log.e(TAG, "getWebView failed: " + t);
            return null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int action = event.getAction();
        Log.d(TAG, "dispatchKeyEvent code=" + code + " action=" + action);

        // Always notify JS with the raw keycode — even for unmapped keys. This
        // lets the on-screen diag panel show *every* key the remote produces,
        // which is critical for boxes (Mecool, OEM) that use non-standard codes.
        if (action == KeyEvent.ACTION_DOWN) {
            notifyRawKey(code);
        }

        String mapped = mapKey(code);
        if (mapped == null) {
            return super.dispatchKeyEvent(event);
        }
        if (action == KeyEvent.ACTION_DOWN) {
            sendToWeb(mapped, true);
            return true;
        }
        if (action == KeyEvent.ACTION_UP) {
            sendToWeb(mapped, false);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void notifyRawKey(int code) {
        WebView wv = getWebView();
        if (wv == null) return;
        final String js =
            "try{if(typeof window.__ultratv_rawkey==='function'){window.__ultratv_rawkey(" + code + ");}}catch(e){}";
        wv.post(() -> wv.evaluateJavascript(js, null));
    }

    private String mapKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:    return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN:  return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT:  return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:      return "enter";
            case KeyEvent.KEYCODE_BACK:       return "back";
            case KeyEvent.KEYCODE_MENU:       return "menu";
            default: return null;
        }
    }

    private void sendToWeb(String action, boolean down) {
        WebView wv = getWebView();
        if (wv == null) return;

        // Path A — call the JS bridge function directly. Always runs (even on keyup).
        if (down) {
            final String js =
                "(function(){try{if(typeof window.__ultratv_remote==='function'){window.__ultratv_remote('" + action + "');}}catch(e){console.error(e);}" +
                // Path B — also fire a KeyboardEvent so anything listening natively reacts.
                "try{var key='" + jsKey(action) + "';if(key){window.dispatchEvent(new KeyboardEvent('keydown',{key:key,code:key,bubbles:true,cancelable:true}));}}catch(e){}})();";
            wv.post(() -> wv.evaluateJavascript(js, null));
        } else {
            final String js =
                "try{var key='" + jsKey(action) + "';if(key){window.dispatchEvent(new KeyboardEvent('keyup',{key:key,code:key,bubbles:true,cancelable:true}));}}catch(e){}";
            wv.post(() -> wv.evaluateJavascript(js, null));
        }
    }

    private static String jsKey(String action) {
        switch (action) {
            case "up":    return "ArrowUp";
            case "down":  return "ArrowDown";
            case "left":  return "ArrowLeft";
            case "right": return "ArrowRight";
            case "enter": return "Enter";
            case "back":  return ""; // handled by JS bridge, no synthetic key
            case "menu":  return "ContextMenu";
            default:      return "";
        }
    }
}
