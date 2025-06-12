package com.example.drawzonesketchapp;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int STORAGE_PERMISSION_REQUEST = 2;
    private static final String TAG = "PencilSketchApp";
    private static final int MAX_IMAGE_DIMENSION = 1000;

    private ImageView imageView;
    private Button btnSelect, btnConvert, btnSave;
    private ProgressBar progressBar;
    private Bitmap originalBitmap, sketchBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        btnSelect = findViewById(R.id.btnSelect);
        btnConvert = findViewById(R.id.btnConvert);
        btnSave = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);

        btnSelect.setOnClickListener(v -> checkPermissionAndOpenGallery());
        btnConvert.setOnClickListener(v -> convertImageToSketch());
        btnSave.setOnClickListener(v -> saveImageToGallery());

        btnConvert.setEnabled(false);
        btnSave.setEnabled(false);
        progressBar.setVisibility(View.GONE);
    }

    private void checkPermissionAndOpenGallery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
        } else {
            openGallery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Permission denied. Cannot access gallery.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
        } catch (Exception e) {
            Log.e(TAG, "Error opening gallery: " + e.getMessage());
            showError("Error opening gallery");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                recycleBitmap(originalBitmap);
                recycleBitmap(sketchBitmap);

                originalBitmap = decodeSampledBitmapFromUri(uri, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION);
                if (originalBitmap != null) {
                    imageView.setImageBitmap(originalBitmap);
                    btnConvert.setEnabled(true);
                    btnSave.setEnabled(false);
                } else {
                    showError("Failed to load image");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading image: " + e.getMessage());
                showError("Error loading image");
            }
        }
    }

    private Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);
        if (inputStream != null) inputStream.close();

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565; // More memory efficient

        inputStream = getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
        if (inputStream != null) inputStream.close();

        return bitmap;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void convertImageToSketch() {
        if (originalBitmap == null) {
            showError("No image selected");
            return;
        }

        new SketchConversionTask().execute(originalBitmap);
    }

    private class SketchConversionTask extends AsyncTask<Bitmap, Void, Bitmap> {
        @Override
        protected void onPreExecute() {
            btnConvert.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Bitmap doInBackground(Bitmap... bitmaps) {
            try {
                return createSketch(bitmaps[0]);
            } catch (Exception e) {
                Log.e(TAG, "Conversion error: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            progressBar.setVisibility(View.GONE);
            btnConvert.setEnabled(true);

            if (result != null) {
                recycleBitmap(sketchBitmap);
                sketchBitmap = result;
                imageView.setImageBitmap(sketchBitmap);
                btnSave.setEnabled(true);
                Toast.makeText(MainActivity.this, "Conversion complete", Toast.LENGTH_SHORT).show();
            } else {
                showError("Conversion failed");
            }
        }
    }

    private Bitmap createSketch(Bitmap original) {
        // Step 1: Convert to grayscale (optimized)
        Bitmap grayscale = toGrayscale(original);
        if (grayscale == null) return null;

        // Step 2: Invert colors
        Bitmap inverted = invert(grayscale);
        recycleBitmap(grayscale);
        if (inverted == null) return null;

        // Step 3: Apply fast blur
        Bitmap blurred = fastBlur(inverted, 5); // Reduced radius for performance
        recycleBitmap(inverted);
        if (blurred == null) return null;

        // Step 4: Color dodge blend
        Bitmap sketch = colorDodge(original, blurred);
        recycleBitmap(blurred);

        return sketch;
    }

    private Bitmap toGrayscale(Bitmap original) {
        try {
            Bitmap grayscale = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(grayscale);
            Paint paint = new Paint();

            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

            canvas.drawBitmap(original, 0, 0, paint);
            return grayscale;
        } catch (Exception e) {
            Log.e(TAG, "Grayscale error: " + e.getMessage());
            return null;
        }
    }

    private Bitmap invert(Bitmap original) {
        try {
            Bitmap inverted = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.RGB_565);
            int[] pixels = new int[original.getWidth() * original.getHeight()];
            original.getPixels(pixels, 0, original.getWidth(), 0, 0, original.getWidth(), original.getHeight());

            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = Color.rgb(
                        255 - Color.red(pixels[i]),
                        255 - Color.green(pixels[i]),
                        255 - Color.blue(pixels[i])
                );
            }

            inverted.setPixels(pixels, 0, original.getWidth(), 0, 0, original.getWidth(), original.getHeight());
            return inverted;
        } catch (Exception e) {
            Log.e(TAG, "Invert error: " + e.getMessage());
            return null;
        }
    }

    private Bitmap fastBlur(Bitmap original, int radius) {
        try {
            // Stack Blur Algorithm - much faster than Gaussian blur
            Bitmap blurred = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.RGB_565);

            int[] pix = new int[original.getWidth() * original.getHeight()];
            original.getPixels(pix, 0, original.getWidth(), 0, 0, original.getWidth(), original.getHeight());

            int w = original.getWidth();
            int h = original.getHeight();

            int wm = w - 1;
            int hm = h - 1;
            int wh = w * h;
            int div = radius + radius + 1;

            int r[] = new int[wh];
            int g[] = new int[wh];
            int b[] = new int[wh];
            int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
            int vmin[] = new int[Math.max(w, h)];

            int divsum = (div + 1) >> 1;
            divsum *= divsum;
            int dv[] = new int[256 * divsum];
            for (i = 0; i < 256 * divsum; i++) {
                dv[i] = (i / divsum);
            }

            yw = yi = 0;

            int[][] stack = new int[div][3];
            int stackpointer;
            int stackstart;
            int[] sir;
            int rbs;
            int r1 = radius + 1;
            int routsum, goutsum, boutsum;
            int rinsum, ginsum, binsum;

            for (y = 0; y < h; y++) {
                rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
                for (i = -radius; i <= radius; i++) {
                    p = pix[yi + Math.min(wm, Math.max(i, 0))];
                    sir = stack[i + radius];
                    sir[0] = (p & 0xff0000) >> 16;
                    sir[1] = (p & 0x00ff00) >> 8;
                    sir[2] = (p & 0x0000ff);
                    rbs = r1 - Math.abs(i);
                    rsum += sir[0] * rbs;
                    gsum += sir[1] * rbs;
                    bsum += sir[2] * rbs;
                    if (i > 0) {
                        rinsum += sir[0];
                        ginsum += sir[1];
                        binsum += sir[2];
                    } else {
                        routsum += sir[0];
                        goutsum += sir[1];
                        boutsum += sir[2];
                    }
                }
                stackpointer = radius;

                for (x = 0; x < w; x++) {
                    r[yi] = dv[rsum];
                    g[yi] = dv[gsum];
                    b[yi] = dv[bsum];

                    rsum -= routsum;
                    gsum -= goutsum;
                    bsum -= boutsum;

                    stackstart = stackpointer - radius + div;
                    sir = stack[stackstart % div];

                    routsum -= sir[0];
                    goutsum -= sir[1];
                    boutsum -= sir[2];

                    if (y == 0) {
                        vmin[x] = Math.min(x + radius + 1, wm);
                    }
                    p = pix[yw + vmin[x]];

                    sir[0] = (p & 0xff0000) >> 16;
                    sir[1] = (p & 0x00ff00) >> 8;
                    sir[2] = (p & 0x0000ff);

                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];

                    rsum += rinsum;
                    gsum += ginsum;
                    bsum += binsum;

                    stackpointer = (stackpointer + 1) % div;
                    sir = stack[(stackpointer) % div];

                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];

                    rinsum -= sir[0];
                    ginsum -= sir[1];
                    binsum -= sir[2];

                    yi++;
                }
                yw += w;
            }

            for (x = 0; x < w; x++) {
                rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
                yp = -radius * w;
                for (i = -radius; i <= radius; i++) {
                    yi = Math.max(0, yp) + x;

                    sir = stack[i + radius];

                    sir[0] = r[yi];
                    sir[1] = g[yi];
                    sir[2] = b[yi];

                    rbs = r1 - Math.abs(i);

                    rsum += r[yi] * rbs;
                    gsum += g[yi] * rbs;
                    bsum += b[yi] * rbs;

                    if (i > 0) {
                        rinsum += sir[0];
                        ginsum += sir[1];
                        binsum += sir[2];
                    } else {
                        routsum += sir[0];
                        goutsum += sir[1];
                        boutsum += sir[2];
                    }

                    if (i < hm) {
                        yp += w;
                    }
                }
                yi = x;
                stackpointer = radius;

                for (y = 0; y < h; y++) {
                    pix[yi] = (0xff000000) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                    rsum -= routsum;
                    gsum -= goutsum;
                    bsum -= boutsum;

                    stackstart = stackpointer - radius + div;
                    sir = stack[stackstart % div];

                    routsum -= sir[0];
                    goutsum -= sir[1];
                    boutsum -= sir[2];

                    if (x == 0) {
                        vmin[y] = Math.min(y + r1, hm) * w;
                    }
                    p = x + vmin[y];

                    sir[0] = r[p];
                    sir[1] = g[p];
                    sir[2] = b[p];

                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];

                    rsum += rinsum;
                    gsum += ginsum;
                    bsum += binsum;

                    stackpointer = (stackpointer + 1) % div;
                    sir = stack[stackpointer];

                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];

                    rinsum -= sir[0];
                    ginsum -= sir[1];
                    binsum -= sir[2];

                    yi += w;
                }
            }

            blurred.setPixels(pix, 0, w, 0, 0, w, h);
            return blurred;
        } catch (Exception e) {
            Log.e(TAG, "Blur error: " + e.getMessage());
            return null;
        }
    }

    private Bitmap colorDodge(Bitmap top, Bitmap bottom) {
        try {
            Bitmap result = Bitmap.createBitmap(top.getWidth(), top.getHeight(), Bitmap.Config.RGB_565);
            int[] topPixels = new int[top.getWidth() * top.getHeight()];
            int[] bottomPixels = new int[top.getWidth() * top.getHeight()];
            int[] resultPixels = new int[top.getWidth() * top.getHeight()];

            top.getPixels(topPixels, 0, top.getWidth(), 0, 0, top.getWidth(), top.getHeight());
            bottom.getPixels(bottomPixels, 0, top.getWidth(), 0, 0, top.getWidth(), top.getHeight());

            for (int i = 0; i < topPixels.length; i++) {
                int topRed = Color.red(topPixels[i]);
                int bottomRed = Color.red(bottomPixels[i]);

                int red = (bottomRed == 255) ? 255 : Math.min(255, (topRed << 8) / (255 - bottomRed));
                resultPixels[i] = Color.rgb(red, red, red);
            }

            result.setPixels(resultPixels, 0, top.getWidth(), 0, 0, top.getWidth(), top.getHeight());
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Color dodge error: " + e.getMessage());
            return null;
        }
    }

    private void saveImageToGallery() {
        if (sketchBitmap == null) {
            showError("No sketch to save");
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
            return;
        }

        new SaveImageTask().execute(sketchBitmap);
    }

    private class SaveImageTask extends AsyncTask<Bitmap, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            btnSave.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Bitmap... bitmaps) {
            try {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String imageFileName = "SKETCH_" + timeStamp + ".jpg";

                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PencilSketches");

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    bitmaps[0].compress(Bitmap.CompressFormat.JPEG, 90, out);
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Save error: " + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressBar.setVisibility(View.GONE);
            btnSave.setEnabled(true);

            if (success) {
                Toast.makeText(MainActivity.this, "Sketch saved to gallery", Toast.LENGTH_SHORT).show();
            } else {
                showError("Failed to save sketch");
            }
        }
    }

    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recycleBitmap(originalBitmap);
        recycleBitmap(sketchBitmap);
    }
}