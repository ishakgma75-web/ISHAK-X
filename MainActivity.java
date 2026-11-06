package com.ishak.x;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.ScanResult;
import org.json.JSONArray;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;

public class MainActivity extends Activity {
    WebView browser;

    public class AndroidBridge {
        @JavascriptInterface
        public String getDeviceInfo() {
            try {
                JSONObject d = new JSONObject();
                d.put("model", Build.MODEL);
                d.put("manufacturer", Build.MANUFACTURER);
                d.put("android", Build.VERSION.RELEASE);
                d.put("sdk", Build.VERSION.SDK_INT);
                d.put("processor", System.getProperty("os.arch"));

                ConnectivityManager cm =
                    (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                Network n = cm.getActiveNetwork();
                NetworkCapabilities c = cm.getNetworkCapabilities(n);

                String type = "OFFLINE";
                if (c != null) {
                    if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                        type = "WIFI";
                    else if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                        type = "MOBILE";
                    else if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                        type = "ETHERNET";
                    else type = "CONNECTED";
                }
                d.put("network", type);
                return d.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String getMonitorInfo() {
            try {
                JSONObject d = new JSONObject();

                Intent battery = registerReceiver(
                    null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                );

                if (battery != null) {
                    int level = battery.getIntExtra("level", -1);
                    int scale = battery.getIntExtra("scale", 100);
                    int temp = battery.getIntExtra("temperature", -1);
                    int status = battery.getIntExtra("status", -1);

                    d.put("battery", scale > 0 ? level * 100 / scale : -1);
                    d.put("temperature", temp >= 0 ? temp / 10.0 : -1);
                    d.put("charging",
                        status == 2 || status == 5);
                }

                ActivityManager am =
                    (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi =
                    new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);

                d.put("totalMemory", mi.totalMem / 1048576);
                d.put("availableMemory", mi.availMem / 1048576);
                d.put("usedMemory",
                    (mi.totalMem - mi.availMem) / 1048576);
                d.put("lowMemory", mi.lowMemory);

                return d.toString();
            } catch (Exception e) {
                return "{\"error\":\"Monitor unavailable\"}";
            }
        }

        @JavascriptInterface
        public String startWifiScan() {
            if (MainActivity.this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> MainActivity.this.requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 71));
                return "PERMISSION_REQUIRED";
            }

            try {
                WifiManager wm = (WifiManager)
                    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm == null || !wm.isWifiEnabled()) return "WIFI_DISABLED";
                return wm.startScan() ? "SCAN_STARTED" : "SCAN_THROTTLED";
            } catch (Exception e) {
                return "SCAN_ERROR";
            }
        }

        @JavascriptInterface
        public String getWifiScanResults() {
            try {
                if (MainActivity.this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    return "[]";
                }

                WifiManager wm = (WifiManager)
                    getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                JSONArray list = new JSONArray();
                if (wm == null || !wm.isWifiEnabled()) return list.toString();

                for (ScanResult r : wm.getScanResults()) {
                    JSONObject item = new JSONObject();
                    item.put("ssid", r.SSID);
                    item.put("bssid", r.BSSID);
                    item.put("signal", r.level);
                    item.put("security", r.capabilities);
                    item.put("frequency", r.frequency);
                    list.put(item);
                }
                return list.toString();
            } catch (Exception e) {
                return "[]";
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        browser = new WebView(this);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        browser.addJavascriptInterface(new AndroidBridge(), "Android");
        browser.setWebViewClient(new WebViewClient());
        setContentView(browser);
        browser.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }
}
