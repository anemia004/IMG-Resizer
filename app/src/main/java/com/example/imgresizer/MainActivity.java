package com.example.imgresizer;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private final static int FILE_CHOOSER_RESULT_CODE = 12345;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        // Expose native save method to JavaScript
        webView.addJavascriptInterface(new AndroidInterface(this), "Android");

        webView.setWebViewClient(new WebViewClient());

        // File chooser (copies file to cache so WebView can read it)
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Cannot open file chooser", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        // No DownloadListener needed – download is handled by the JavaScript interface

        webView.loadUrl("file:///android_asset/index.html");
    }

    // JavaScript interface for saving images
    public class AndroidInterface {
        private final Context context;

        AndroidInterface(Context c) {
            context = c;
        }

        @JavascriptInterface
        public void saveImage(String base64Data, String fileName) {
            try {
                byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (bitmap == null) {
                    showToast("Failed to decode image");
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use MediaStore (Android 10+)
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/*");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        OutputStream out = context.getContentResolver().openOutputStream(uri);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        out.close();
                        showToast("Saved to Downloads");
                    }
                } else {
                    // For older Android, save directly to Downloads folder
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File file = new File(downloadsDir, fileName);
                    FileOutputStream out = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.close();
                    // Notify gallery
                    MediaStore.Images.Media.insertImage(context.getContentResolver(), file.getAbsolutePath(), fileName, null);
                    showToast("Saved to Downloads");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showToast("Error saving image");
            }
        }

        private void showToast(final String msg) {
            runOnUiThread(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                Uri originalUri = data.getData();
                if (originalUri != null) {
                    Uri localUri = copyToLocalFile(originalUri);
                    if (localUri != null) {
                        results = new Uri[]{localUri};
                    }
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    /**
     * Copies the content from a content:// URI to a temp file in the app's cache dir,
     * and returns a file:// URI that can be safely read by the WebView.
     */
    private Uri copyToLocalFile(Uri sourceUri) {
        try {
            String mime = getContentResolver().getType(sourceUri);
            String ext = ".jpg";
            if (mime != null) {
                if (mime.equals("image/png")) ext = ".png";
                else if (mime.equals("image/webp")) ext = ".webp";
                else if (mime.equals("image/gif")) ext = ".gif";
            }

            File tempFile = File.createTempFile("img_resizer_", ext, getCacheDir());
            InputStream in = getContentResolver().openInputStream(sourceUri);
            FileOutputStream out = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
            return Uri.fromFile(tempFile);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open image", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
