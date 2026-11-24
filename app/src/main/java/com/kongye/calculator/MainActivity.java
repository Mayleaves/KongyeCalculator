package com.kongye.calculator;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String PAGE = "https://appassets.androidplatform.net/calculator.html";
    private static final String RELATIONSHIP = "https://appassets.androidplatform.net/relationship.min.js";
    private static final String BOOTSTRAP = "/* ANDROID_LEGACY_STATE */ {}";
    private static final int SAVE_ICON = 1;
    private WebView web;
    private BrandSplashView startup;
    private SharedPreferences appearance;
    private String pendingIcon;
    private AppUpdates updates;

    @Override protected void onCreate(Bundle state) {
        SharedPreferences previous = getSharedPreferences("calculator", MODE_PRIVATE);
        appearance = getSharedPreferences("calculator_shell", MODE_PRIVATE);
        boolean systemDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        boolean dark = appearance.getBoolean("dark", previous.getBoolean("dark", systemDark));
        applyStartupTheme(dark);
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 31) {
            // The native brand view owns startup; do not precede it with a second icon animation.
            getSplashScreen().setOnExitAnimationListener(splash -> splash.remove());
        }
        updateSystemBars(dark);
        pendingIcon = state == null ? null : state.getString("pendingIcon");
        FrameLayout content = new FrameLayout(this);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        startup = new BrandSplashView(this, dark, () -> initializeWeb(content, previous), () -> {
            content.removeView(startup);
            startup = null;
        });
        content.addView(startup, new FrameLayout.LayoutParams(-1, -1));
        setContentView(content);
        content.requestApplyInsets();
    }

    private void initializeWeb(FrameLayout content, SharedPreferences previous) {
        if (isFinishing() || isDestroyed()) return;
        try {
            byte[] page = buildPage(previous);
            web = new WebView(this);
            updates = new AppUpdates(this, event -> {
                String payload = event.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
                runOnUiThread(() -> {
                    if (web != null) web.evaluateJavascript("window.onAppUpdateEvent?.(" + payload + ")", null);
                });
            });
            web.setBackgroundColor(Color.TRANSPARENT);
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.getSettings().setAllowFileAccess(false);
            web.getSettings().setAllowContentAccess(false);
            web.getSettings().setBlockNetworkLoads(true);
            web.addJavascriptInterface(new CalculatorHost(), "CalculatorHost");
            web.setWebViewClient(new WebViewClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if ("GET".equals(request.getMethod())) {
                        String url = request.getUrl().toString();
                        if (PAGE.equals(url)) {
                            return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(page));
                        }
                        if (RELATIONSHIP.equals(url)) {
                            try {
                                return new WebResourceResponse("application/javascript", "UTF-8",
                                    getAssets().open("relationship.min.js"));
                            } catch (IOException ignored) {}
                        }
                    }
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null,
                        new ByteArrayInputStream(new byte[0]));
                }

                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return !PAGE.equals(request.getUrl().toString());
                }

                @Override public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript("document.documentElement.dataset.ready", result -> {
                        if (!"\"true\"".equals(result) && !isFinishing()) openClassic();
                        else if (!isFinishing()) view.postVisualStateCallback(0, new WebView.VisualStateCallback() {
                            @Override public void onComplete(long requestId) {
                                if (startup != null) startup.pageReady();
                            }
                        });
                    });
                }

                @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    openClassic();
                    return true;
                }
            });
            content.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
            web.loadUrl(PAGE);
        } catch (RuntimeException | IOException | JSONException failure) {
            openClassic();
        }
    }

    private byte[] buildPage(SharedPreferences previous) throws IOException, JSONException {
        try (InputStream file = getAssets().open("calculator.html")) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int count; (count = file.read(chunk)) != -1;) buffer.write(chunk, 0, count);
            String html = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            String legacy = legacyState(previous).toString().replace("<", "\\u003c")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
            String theme = appearance.getBoolean("dark", false) ? "dark" : "light";
            return html.replace(BOOTSTRAP, legacy)
                .replace("<html lang=\"zh-CN\" data-theme=\"light\">",
                    "<html lang=\"zh-CN\" data-theme=\"" + theme + "\" data-native-theme=\"" + theme + "\">")
                .replace("</head>", "<style>#brand-splash {display:none!important;}</style></head>")
                .getBytes(StandardCharsets.UTF_8);
        }
    }

    private void applyStartupTheme(boolean dark) {
        appearance.edit().putBoolean("dark", dark).apply();
        if (Build.VERSION.SDK_INT >= 31) {
            UiModeManager manager = getSystemService(UiModeManager.class);
            if (manager != null) manager.setApplicationNightMode(
                dark ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
        }
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
            Color.parseColor(dark ? "#1C1F27" : "#FFFFFF")));
        syncLauncherIcon(dark);
    }

    private void syncLauncherIcon(boolean dark) {
        PackageManager manager = getPackageManager();
        ComponentName light = new ComponentName(this, getPackageName() + ".LauncherLight");
        ComponentName night = new ComponentName(this, getPackageName() + ".LauncherDark");
        int lightState = manager.getComponentEnabledSetting(light);
        int darkState = manager.getComponentEnabledSetting(night);
        boolean lightEnabled = lightState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            || lightState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        boolean darkEnabled = darkState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        if (lightEnabled == !dark && darkEnabled == dark) return;
        int enabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        int disabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (Build.VERSION.SDK_INT >= 33) {
            manager.setComponentEnabledSettings(List.of(
                new PackageManager.ComponentEnabledSetting(light, dark ? disabled : enabled, PackageManager.DONT_KILL_APP),
                new PackageManager.ComponentEnabledSetting(night, dark ? enabled : disabled, PackageManager.DONT_KILL_APP)));
        } else {
            manager.setComponentEnabledSetting(dark ? night : light, enabled, PackageManager.DONT_KILL_APP);
            manager.setComponentEnabledSetting(dark ? light : night, disabled, PackageManager.DONT_KILL_APP);
        }
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateSystemBars(appearance.getBoolean("dark", false));
    }

    private void updateSystemBars(boolean dark) {
        int background = Color.parseColor(dark ? "#1C1F27" : "#FFFFFF");
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setBackgroundColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0
            : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void openClassic() {
        if (isFinishing()) return;
        disposeWebView();
        startActivity(new Intent(this, ClassicActivity.class));
        finish();
    }

    @Override public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("typeof androidBack === 'function' && androidBack()", handled -> {
            if (!"true".equals(handled) && !isFinishing()) super.onBackPressed();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (updates != null) updates.resume();
        if (web != null) web.evaluateJavascript(
            "if(typeof clearTemporaryResults === 'function') clearTemporaryResults()", null);
    }

    @Override protected void onPause() {
        if (web != null) web.evaluateJavascript("if(typeof retain === 'function') retain()", null);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("pendingIcon", pendingIcon);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        disposeWebView();
        super.onDestroy();
    }

    private void disposeWebView() {
        if (updates != null) { updates.close(); updates = null; }
        if (web == null) return;
        if (web.getParent() instanceof FrameLayout) ((FrameLayout) web.getParent()).removeView(web);
        web.destroy();
        web = null;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SAVE_ICON) return;
        String svg = pendingIcon;
        pendingIcon = null;
        if (result != RESULT_OK || data == null || data.getData() == null || svg == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IOException();
            output.write(svg.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "图标已保存", Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException error) {
            Toast.makeText(this, "无法保存图标", Toast.LENGTH_SHORT).show();
        }
    }

    private final class CalculatorHost {
        @JavascriptInterface public String getUpdateState() { return updates == null ? "{}" : updates.snapshot(); }
        @JavascriptInterface public void checkUpdates() { if (updates != null) updates.check(); }
        @JavascriptInterface public void downloadUpdate() { if (updates != null) updates.download(); }
        @JavascriptInterface public void cancelUpdateDownload() { if (updates != null) updates.cancel(); }
        @JavascriptInterface public void installUpdate() { if (updates != null) updates.install(); }
        @JavascriptInterface public void getReleaseHistory(int id, int page, String search) { if (updates != null) updates.history(id, page, search); }
        @JavascriptInterface public void openGitHub(String url) { if (updates != null && url != null) updates.openGitHub(url); }
        @JavascriptInterface public void setDark(boolean dark) {
            appearance.edit().putBoolean("dark", dark).apply();
            runOnUiThread(() -> {
                applyStartupTheme(dark);
                updateSystemBars(dark);
            });
        }

        @JavascriptInterface public void saveIcon(boolean dark, String svg) {
            if (svg == null || svg.length() > 10000 || !svg.startsWith("<svg ")) return;
            runOnUiThread(() -> {
                if (pendingIcon != null || isFinishing()) return;
                pendingIcon = svg;
                Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/svg+xml").putExtra(Intent.EXTRA_TITLE, dark ? "kongye-dark.svg" : "kongye-light.svg");
                try { startActivityForResult(picker, SAVE_ICON); }
                catch (android.content.ActivityNotFoundException error) {
                    pendingIcon = null;
                    Toast.makeText(MainActivity.this, "系统未提供文件保存界面", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface public String calculate(String expression, String bindings, String definitions) {
            if (expression == null || expression.length() > 2000 || bindings == null || bindings.length() > 8192 ||
                    definitions == null || definitions.length() > 32000) return "{}";
            try {
                JSONObject data = new JSONObject(bindings);
                Map<String, Double> values = new HashMap<>();
                for (Iterator<String> names = data.keys(); names.hasNext();) {
                    String name = names.next();
                    double value = data.optDouble(name);
                    if (Double.isFinite(value)) values.put(name, value);
                }
                Calculator.Value result = evaluate(expression, values, new JSONObject(definitions), 0);
                JSONObject response = new JSONObject().put("number", result.number);
                if (result.exact != null && result.exact.fitsDisplay()) {
                    response.put("fraction", new JSONArray().put(result.exact.top.toString()).put(result.exact.bottom.toString()));
                }
                return response.toString();
            } catch (JSONException | RuntimeException | StackOverflowError problem) {
                return "{}";
            }
        }
    }

    private static Calculator.Value evaluate(String source, Map<String, Double> values, JSONObject functions, int depth) {
        if (depth >= 16) throw new IllegalArgumentException("函数嵌套过深");
        String expression = source.replace("logbase(", "log(").replace("log10(", "log(").replace(':', '÷');
        int equal = expression.indexOf('=');
        if (equal > 0 && "y".equals(expression.substring(0, equal).trim())) expression = expression.substring(equal + 1);
        return Calculator.evaluateDetailed(expression, true, values, (name, argument) -> {
            if (!functions.has(name)) throw new IllegalArgumentException("未知函数");
            Map<String, Double> context = new HashMap<>(values);
            context.put("x", argument.number);
            return evaluate(functions.optString(name), context, functions, depth + 1);
        });
    }

    private static JSONObject legacyState(SharedPreferences previous) throws JSONException {
        JSONObject result = new JSONObject()
            .put("theme", previous.getBoolean("dark", false) ? "dark" : "light")
            .put("mode", previous.getInt("keyboard", 0))
            .put("confirmed", previous.getBoolean("confirmed", false))
            .put("lastAnswer", Double.toString(number(previous, "ans")))
            .put("previousAnswer", Double.toString(number(previous, "previousAns")))
            .put("draft", legacyTokens(previous.getString("expression", "")));
        JSONObject variables = new JSONObject();
        for (String name : "x y z a b c k m n ρ θ η h p r".split(" ")) {
            double value = number(previous, ("x".equals(name) || "y".equals(name)) ? name : "var_" + name);
            if (Double.isFinite(value) && value != 0) variables.put(name, value);
        }
        result.put("variables", variables);
        try { result.put("functions", new JSONObject(previous.getString("customFunctions", "{}"))); }
        catch (JSONException ignored) {}
        try {
            JSONArray keys = new JSONArray();
            JSONArray layout = new JSONArray(previous.getString("keyLayout", "[]"));
            for (int i = 0; i < layout.length(); i++) {
                JSONArray flat = new JSONArray();
                JSONArray group = layout.getJSONArray(i);
                for (int j = 0; j < group.length(); j++) {
                    JSONArray row = group.getJSONArray(j);
                    for (int k = 0; k < row.length(); k++) flat.put(row.getString(k));
                }
                keys.put(flat);
            }
            result.put("keys", keys);
        } catch (JSONException ignored) {}
        JSONArray history = new JSONArray();
        try {
            JSONArray records = new JSONArray(previous.getString("history", "[]"));
            DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy/M/d").withZone(ZoneId.systemDefault());
            for (int i = 0; i < records.length(); i++) {
                JSONObject old = records.optJSONObject(i);
                if (old == null) continue;
                String expression = old.optString("expression");
                long timestamp = old.optLong("createdAt");
                history.put(new JSONObject().put("expression", expression).put("result", old.optString("result"))
                    .put("exact", old.optString("fraction")).put("date", timestamp > 0 ? date.format(Instant.ofEpochMilli(timestamp)) : "日期未记录")
                    .put("rawTokens", legacyTokens(old.optString("editor", expression))));
            }
        } catch (JSONException ignored) {}
        return result.put("history", history);
    }

    private static double number(SharedPreferences preferences, String name) {
        return Double.longBitsToDouble(preferences.getLong(name, 0));
    }

    private static JSONArray legacyTokens(String raw) throws JSONException {
        MathDocument document = new MathDocument();
        document.restore(raw, raw.length());
        JSONArray tokens = new JSONArray();
        appendLegacyTokens(document.parse(), tokens);
        return tokens;
    }

    private static void appendLegacyTokens(List<MathDocument.Box> boxes, JSONArray output) throws JSONException {
        for (MathDocument.Box box : boxes) {
            if (box.symbol == MathDocument.GROUP) {
                for (int i = 0; i < box.fields.size(); i++) {
                    if (i > 0) output.put("\n");
                    appendLegacyTokens(box.fields.get(i).boxes, output);
                }
            } else if (MathDocument.template(box.symbol) && box.fields.size() >= 2) {
                String key = box.symbol == MathDocument.LOG ? "logₐ" : box.symbol == MathDocument.POWER ? "xʸ"
                    : box.symbol == MathDocument.ROOT ? "ⁿ√" : "a/b";
                JSONArray fields = new JSONArray();
                for (MathDocument.Field field : box.fields) fields.put(legacyField(field.boxes));
                output.put(new JSONObject().put("key", key).put("fields", fields));
            } else if (!MathDocument.template(box.symbol)) {
                output.put(String.valueOf(MathDocument.visible(box.symbol)));
            }
        }
    }

    private static String legacyField(List<MathDocument.Box> boxes) {
        StringBuilder plain = new StringBuilder();
        for (MathDocument.Box box : boxes) {
            if (!MathDocument.template(box.symbol)) plain.append(MathDocument.visible(box.symbol));
            else if (box.fields.size() >= 2 && box.symbol != MathDocument.GROUP) {
                String a = legacyField(box.fields.get(0).boxes), b = legacyField(box.fields.get(1).boxes);
                if (box.symbol == MathDocument.FRACTION || box.symbol == MathDocument.POWER) {
                    plain.append("((").append(a).append(box.symbol == MathDocument.FRACTION ? ")/(" : ")^(").append(b).append("))");
                } else {
                    plain.append(box.symbol == MathDocument.ROOT ? "root(" : "logbase(").append(a).append(',').append(b).append(')');
                }
            }
        }
        return plain.toString();
    }
}
