package com.ultratv.tv;

import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

/**
 * Bridges Android TV / Fire TV / Google TV remote D-pad input into the WebView.
 *
 * Android delivers DPAD keycodes to the foreground Activity. By default the
 * WebView ignores them — so arrow keys on a remote never reach JavaScript and
 * spatial navigation appears broken. We intercept them here and dispatch a real
 * KeyboardEvent on `window` inside the WebView so the JS spatial-nav script can
 * react. OK / Center also fires Enter.
 */
public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ExternalPlayerPlugin.class);
        super.onCreate(savedInstanceState);

        WebView wv = (WebView) getBridge().getWebView();
        if (wv != null) {
            wv.setFocusable(true);
            wv.setFocusableInTouchMode(true);
            wv.requestFocus();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        String jsKey = mapKey(event.getKeyCode());
        if (jsKey == null) {
            return super.dispatchKeyEvent(event);
        }
        if (action == KeyEvent.ACTION_DOWN) {
            dispatchToWeb(jsKey, true);
            return true;
        }
        if (action == KeyEvent.ACTION_UP) {
            dispatchToWeb(jsKey, false);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private String mapKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:    return "ArrowUp";
            case KeyEvent.KEYCODE_DPAD_DOWN:  return "ArrowDown";
            case KeyEvent.KEYCODE_DPAD_LEFT:  return "ArrowLeft";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "ArrowRight";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:      return "Enter";
            case KeyEvent.KEYCODE_BACK:       return "GoBack";
            case KeyEvent.KEYCODE_MENU:       return "ContextMenu";
            default: return null;
        }
    }

    private void dispatchToWeb(String key, boolean down) {
        WebView wv = (WebView) getBridge().getWebView();
        if (wv == null) return;
        if ("GoBack".equals(key)) {
            if (!down) return;
            wv.post(() -> wv.evaluateJavascript(
                "(function(){var e=new CustomEvent('androidback',{cancelable:true});var ok=window.dispatchEvent(e);if(!e.defaultPrevented){if(history.length>1){history.back();}}})();",
                null));
            return;
        }
        final String safeKey = key.replace("'", "\\'");
        final String type = down ? "keydown" : "keyup";
        wv.post(() -> wv.evaluateJavascript(
            "window.dispatchEvent(new KeyboardEvent('" + type + "',{key:'" + safeKey +
                "',code:'" + safeKey +
                "',bubbles:true,cancelable:true}));", null));
    }
}
