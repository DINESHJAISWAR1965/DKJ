package com.bcube.invest;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bcube Invest - Developed by Dinesh Jaiswar. Sirf WebView + khabar fetch + HTML save. */
public class MainActivity extends Activity {
    private static final String UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";
    private WebView wv;
    private final ExecutorService pool = Executors.newFixedThreadPool(16);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        wv = new WebView(this);
        setContentView(wv);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                String sc = u.getScheme();
                if ("http".equals(sc) || "https".equals(sc)) {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));   // khabar browser me khulegi
                }
                return true;
            }
        });
        wv.addJavascriptInterface(new Bridge(), "Android");
        wv.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
    }

    private String get(String u) throws IOException {
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Language", "en-IN,en;q=0.9,hi;q=0.8");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("redirect error");
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            if (code >= 400) { c.disconnect(); throw new IOException("HTTP " + code); }
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                return bo.toString("UTF-8");
            } finally { c.disconnect(); }
        }
        throw new IOException("too many redirects");
    }

    private class Bridge {
        @JavascriptInterface
        public void fetch(final int id, final String url) {
            pool.execute(() -> {
                String out;
                boolean ok = true;
                try { out = get(url); } catch (Exception e) { ok = false; out = String.valueOf(e.getMessage()); }
                final String js = "__fd(" + id + "," + ok + "," + JSONObject.quote(out) + ")";
                runOnUiThread(() -> wv.evaluateJavascript(js, null));
            });
        }

        /** Sirf ek HTML file: seedhe phone ke Downloads me. */
        @JavascriptInterface
        public void save(String name, String content) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "text/html");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) throw new IOException("file nahi ban saki");
                try (OutputStream o = getContentResolver().openOutputStream(u)) {
                    o.write(content.getBytes("UTF-8"));
                }
                toast("Saved: Downloads/" + name);
            } catch (Exception e) {
                toast("Save error: " + e.getMessage());
            }
        }
    }
}
