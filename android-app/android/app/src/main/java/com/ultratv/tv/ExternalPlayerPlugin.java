package com.ultratv.tv;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Bridge that hands a stream URL off to a native Android player so codecs the
 * WebView can't decode (H.265/HEVC, AC3, EAC3, raw MPEG-TS, MKV, MOV…) play fine.
 *
 * Three modes:
 *   - openInVlc     : explicit VLC for Android intent (org.videolan.vlc), with
 *                     optional UA / Referer extras.
 *   - openInExo     : preferred ExoPlayer-based viewers (Just Player, Next Player,
 *                     MX Player), falling back to the system chooser.
 *   - openExternal  : let the user pick — ACTION_VIEW with the right MIME type.
 */
@CapacitorPlugin(name = "ExternalPlayer")
public class ExternalPlayerPlugin extends Plugin {

    private static final String VLC_PACKAGE = "org.videolan.vlc";

    @PluginMethod
    public void openInVlc(PluginCall call) {
        String url = call.getString("url");
        if (url == null) { call.reject("url is required"); return; }
        String title = call.getString("title", "Ultra TV");
        String userAgent = call.getString("userAgent");
        String referer = call.getString("referer");

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setPackage(VLC_PACKAGE);
        intent.setDataAndTypeAndNormalize(Uri.parse(url), guessMime(url));
        intent.putExtra("title", title);
        intent.putExtra("from_start", true);
        if (userAgent != null && !userAgent.isEmpty()) {
            Bundle headers = new Bundle();
            headers.putString("User-Agent", userAgent);
            if (referer != null && !referer.isEmpty()) headers.putString("Referer", referer);
            intent.putExtra("http-headers", headers);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (isInstalled(VLC_PACKAGE)) {
            try {
                getContext().startActivity(intent);
                resolve(call, "vlc");
            } catch (Exception e) {
                call.reject("VLC failed to launch: " + e.getMessage());
            }
        } else {
            call.reject("VLC for Android is not installed (org.videolan.vlc).");
        }
    }

    @PluginMethod
    public void openInExo(PluginCall call) {
        String url = call.getString("url");
        if (url == null) { call.reject("url is required"); return; }
        String title = call.getString("title", "Ultra TV");

        // Try the most popular ExoPlayer-based viewers first, fall back to any handler.
        String[] preferred = new String[]{
            "com.brouken.player",   // Just Player
            "com.tpstream.player",  // Next Player
            "com.mxtech.videoplayer.ad",   // MX Player free
            "com.mxtech.videoplayer.pro"   // MX Player pro
        };

        for (String pkg : preferred) {
            if (isInstalled(pkg)) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setPackage(pkg);
                intent.setDataAndTypeAndNormalize(Uri.parse(url), guessMime(url));
                intent.putExtra("title", title);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    getContext().startActivity(intent);
                    resolve(call, pkg);
                    return;
                } catch (Exception ignored) { /* try next */ }
            }
        }

        // Fallback: open the system chooser.
        Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        fallback.setType(guessMime(url));
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(Intent.createChooser(fallback, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            resolve(call, "system-chooser");
        } catch (Exception e) {
            call.reject("No external player available: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openExternal(PluginCall call) {
        String url = call.getString("url");
        if (url == null) { call.reject("url is required"); return; }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setType(guessMime(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(Intent.createChooser(intent, "Open stream with…")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            resolve(call, "system-chooser");
        } catch (Exception e) {
            call.reject("No external player available: " + e.getMessage());
        }
    }

    @PluginMethod
    public void availability(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("vlc", isInstalled(VLC_PACKAGE));
        ret.put("justPlayer", isInstalled("com.brouken.player"));
        ret.put("mxPlayer", isInstalled("com.mxtech.videoplayer.ad") || isInstalled("com.mxtech.videoplayer.pro"));
        ret.put("nextPlayer", isInstalled("com.tpstream.player"));
        call.resolve(ret);
    }

    private boolean isInstalled(String pkg) {
        try {
            getContext().getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void resolve(PluginCall call, String launched) {
        JSObject ret = new JSObject();
        ret.put("launched", launched);
        call.resolve(ret);
    }

    private static String guessMime(String url) {
        String lower = url.toLowerCase();
        int q = lower.indexOf('?');
        if (q > 0) lower = lower.substring(0, q);
        if (lower.endsWith(".m3u8")) return "application/x-mpegURL";
        if (lower.endsWith(".mpd")) return "application/dash+xml";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".ts")) return "video/mp2t";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".webm")) return "video/webm";
        return "video/*";
    }
}
