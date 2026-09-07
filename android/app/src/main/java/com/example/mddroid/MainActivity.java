package com.example.mddroid;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 安卓 Markdown 阅读器（原型封装）。
 *
 * 把 theme-preview/index.html（含 phycat 主题）原样跑在一个 WebView 里，
 * 通过 AndroidBridge 把两件浏览器做不到的事接到原生：
 *   - pickFile()   → SAF 选一个 md 文件
 *   - pickFolder() → SAF 选一个目录，递归把其中所有 .md 收进文件库
 * 选择结果经 window.mdReader.addFiles([...]) 注入网页。
 * 也支持其它 App「打开 / 分享」一个 md 给本应用。
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK_FILE = 1001;
    private static final int REQ_PICK_TREE = 1002;

    private WebView web;
    private boolean pageLoaded = false;
    private final List<PendingDoc> pending = new ArrayList<PendingDoc>();

    /** 一份待注入网页的文档 */
    private static final class PendingDoc {
        final String name, path, text;
        PendingDoc(String name, String path, String text) {
            this.name = name; this.path = path; this.text = text;
        }
    }

    /* ==================== 生命周期 ==================== */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // 侧栏折叠/页签记忆用到 localStorage
        s.setAllowFileAccess(true);
        if (Build.VERSION.SDK_INT >= 16) {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        }
        if (Build.VERSION.SDK_INT >= 19) {
            WebView.setWebContentsDebuggingEnabled(true); // 便于 USB 调试
        }

        web.setWebViewClient(new Client());
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        web.loadUrl("file:///android_asset/app/index.html");

        handleIncoming(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncoming(intent);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) { web.goBack(); return; }
        super.onBackPressed();
    }

    /* ==================== 网页容器 ==================== */

    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (u != null && (u.getScheme().equals("http") || u.getScheme().equals("https"))) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true; // 外部链接交给浏览器
            }
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageLoaded = true;
            flushPending();
        }
    }

    /* ==================== 原生桥（网页可调用） ==================== */

    private final class Bridge {
        @JavascriptInterface
        public void pickFile() {
            runOnUiThread(new Runnable() { public void run() { startPickFile(); } });
        }

        @JavascriptInterface
        public void pickFolder() {
            runOnUiThread(new Runnable() { public void run() { startPickFolder(); } });
        }
    }

    private void startPickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/markdown", "text/x-markdown", "text/plain",
                "application/octet-stream", "application/zip"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(i, "选择 Markdown 文件"), REQ_PICK_FILE);
        } catch (Exception e) { toast("没有可用的文件选择器"); }
    }

    private void startPickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_PICK_TREE);
        } catch (Exception e) { toast("没有可用的目录选择器"); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_PICK_FILE) {
            Uri uri = data.getData();
            if (uri != null) handleSingleFile(uri);
        } else if (requestCode == REQ_PICK_TREE) {
            Uri tree = data.getData();
            if (tree != null) handleFolderTree(tree);
        }
    }

    /* ==================== 外部打开/分享 ==================== */

    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        final String action = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream instanceof Uri) uri = (Uri) stream;
        }
        if (uri != null) {
            PendingDoc d = readUri(uri, "导入");
            if (d != null) queue(d);
            else toast("无法读取该文件");
        } else if (Intent.ACTION_SEND.equals(action)) {
            // 有些分享没有附件，直接把纯文本当成 md
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && text.length() > 0) {
                queue(new PendingDoc("分享的文档.md", "导入", text));
            }
        }
    }

    /* ==================== 单文件 / 文件夹处理 ==================== */

    private void handleSingleFile(final Uri uri) {
        PendingDoc d = readUri(uri, "导入");
        if (d != null) queue(d); else toast("无法读取该文件");
    }

    private void handleFolderTree(final Uri treeUri) {
        final String rootDocId = DocumentsContract.getTreeDocumentId(treeUri);
        new Thread(new Runnable() {
            @Override public void run() {
                final List<PendingDoc> found = new ArrayList<PendingDoc>();
                try { collect(treeUri, rootDocId, "", found, 0); }
                catch (Exception ignored) {}
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (found.isEmpty()) { toast("该目录里没有 .md 文件"); return; }
                        queueAll(found);
                    }
                });
            }
        }).start();
    }

    /** 递归收集目录树下的 md，最多 8 层 / 300 个文件，防止过深目录拖垮 */
    private void collect(Uri tree, String docId, String rel,
                         List<PendingDoc> out, int depth) throws Exception {
        if (depth > 8 || out.size() >= 300) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor cur = getContentResolver().query(children,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null);
        if (cur != null) {
            try {
                while (cur.moveToNext() && out.size() < 300) {
                    String id = cur.getString(0);
                    String name = cur.getString(1);
                    String mime = cur.getString(2);
                    String childRel = rel.isEmpty() ? name : rel + "/" + name;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        collect(tree, id, childRel, out, depth + 1);
                    } else if (isMd(name)) {
                        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                        PendingDoc d = readUri(docUri, childRel);
                        if (d != null) out.add(d);
                    }
                }
            } finally { cur.close(); }
        }
    }

    /* ==================== 读取工具 ==================== */

    private PendingDoc readUri(Uri uri, String path) {
        String name = queryDisplayName(uri);
        if (name == null) name = guessName(uri);
        if (name == null) name = "未命名文档.md";
        byte[] bytes = readBytes(uri);
        if (bytes == null) return null;
        // 去掉 UTF-8 BOM（EF BB BF）
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            byte[] noBom = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, noBom, 0, noBom.length);
            bytes = noBom;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return new PendingDoc(name, path, text);
    }

    private String queryDisplayName(Uri uri) {
        try {
            Cursor c = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) return c.getString(0);
                } finally { c.close(); }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String guessName(Uri uri) {
        String last = uri.getLastPathSegment();
        if (last == null) return null;
        int idx = last.indexOf('/');
        return idx >= 0 ? last.substring(idx + 1) : last;
    }

    private byte[] readBytes(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            try {
                while ((n = in.read(buf)) != -1) bout.write(buf, 0, n);
            } finally { in.close(); }
            return bout.toByteArray();
        } catch (Exception e) { return null; }
    }

    private boolean isMd(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    /* ==================== 注入网页 ==================== */

    private void queue(PendingDoc d) { pending.add(d); flushPending(); }
    private void queueAll(List<PendingDoc> list) {
        if (pageLoaded) { push(list); } else { pending.addAll(list); }
    }

    private void flushPending() {
        if (!pageLoaded || pending.isEmpty()) return;
        List<PendingDoc> list = new ArrayList<PendingDoc>(pending);
        pending.clear();
        push(list);
    }

    private void push(final List<PendingDoc> list) {
        if (list == null || list.isEmpty()) return;
        final String json = toJson(list);
        runOnUiThread(new Runnable() {
            @Override public void run() {
                web.evaluateJavascript("window.mdReader && window.mdReader.addFiles(" + json + ");", null);
            }
        });
    }

    private String toJson(List<PendingDoc> list) {
        JSONArray arr = new JSONArray();
        for (PendingDoc d : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", d.name);
                o.put("path", d.path);
                o.put("text", d.text);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
