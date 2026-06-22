package com.example.imgresizer;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private final static int FILE_CHOOSER_RESULT_CODE = 12345;
    private final static int STORAGE_PERMISSION_REQUEST_CODE = 100;

    private String pendingBase64 = null;
    private String pendingFileName = null;

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

        webView.addJavascriptInterface(new AndroidInterface(this), "Android");

        webView.setWebViewClient(new WebViewClient());

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

        webView.loadUrl("file:///android_asset/index.html");
    }

    // JavaScript interface – requests permission on older Android, then saves
    public class AndroidInterface {
        private final Context context;

        AndroidInterface(Context c) { context = c; }

        @JavascriptInterface
        public void saveImage(String base64Data, String fileName) {
            // On Android 9 and below we still need runtime storage permission
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    pendingBase64 = base64Data;
                    pendingFileName = fileName;
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            STORAGE_PERMISSION_REQUEST_CODE);
                    return;
                }
            }
            // Already granted (or not needed on 10+) → save now
            performSave(base64Data, fileName);
        }

        private void performSave(String base64Data, String fileName) {
            try {
                byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (bitmap == null) {
                    showToast("Failed to decode image");
                    return;
                }

                // Determine the correct compress format from file extension
                String lowerName = fileName.toLowerCase();
                Bitmap.CompressFormat format;
                if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                    format = Bitmap.CompressFormat.JPEG;
                } else if (lowerName.endsWith(".webp")) {
                    format = Bitmap.CompressFormat.WEBP;
                } else {
                    format = Bitmap.CompressFormat.PNG;   // fallback
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ – use MediaStore, no permission required
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/*");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        OutputStream out = context.getContentResolver().openOutputStream(uri);
                        bitmap.compress(format, 92, out);   // high quality, adjust if needed
                        out.close();
                        showToast("Saved to Downloads");
                    } else {
                        showToast("Could not create file");
                    }
                } else {
                    // Android 9 and below – direct file with permission
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadsDir.exists()) downloadsDir.mkdirs();
                    File file = new File(downloadsDir, fileName);
                    FileOutputStream out = new FileOutputStream(file);
                    bitmap.compress(format, 92, out);
                    out.close();
                    // Notify system media scanner
                    MediaStore.Images.Media.insertImage(context.getContentResolver(),
                            file.getAbsolutePath(), fileName, null);
                    showToast("Saved to Downloads");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showToast("Error saving image: " + e.getMessage());
            }
        }

        private void showToast(final String msg) {
            runOnUiThread(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
        }
    }

    // Handle permission result for older Android versions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingBase64 != null && pendingFileName != null) {
                    new AndroidInterface(this).performSave(pendingBase64, pendingFileName);
                    pendingBase64 = null;
                    pendingFileName = null;
                }
            } else {
                Toast.makeText(this, "Storage permission denied – cannot save", Toast.LENGTH_SHORT).show();
            }
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
