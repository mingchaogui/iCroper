package com.mingchaogui.image.crop.util;


import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class BitmapOperator {

    protected static final String TAG = BitmapOperator.class.getName();

    public static BitmapFactory.Options getBitmapOption() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inPurgeable = true;
        options.inInputShareable = true;

        return options;
    }

    /**
     *
     * @param context
     * @param outUri
     * @param bitmap
     * @return 是否保存成功
     */
    public static SaveResult saveToDisk(Context context, Uri outUri, Bitmap bitmap) {
        SaveResult saveResult = new SaveResult();

        if (outUri == null) {
            Log.e(TAG, "没有指定要保存的位置，将自动指定一个位置");

            File file = new File(context.getCacheDir() + "/crop" + UUID.randomUUID() + ".png");
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "创建文件失败");

                saveResult.isOK = false;
                return saveResult;
            }
            outUri = Uri.fromFile(file);
        }

        Log.d(TAG, "保存裁图至" + outUri.toString());

        Bitmap.CompressFormat outFormat;
        String suffix = UriUtil.getExtensionFromMimeType(context, outUri);
        if (suffix != null)
            suffix = suffix.toLowerCase();
        if ("png".equals(suffix)) {
            outFormat = Bitmap.CompressFormat.PNG;
        } else {
            outFormat = Bitmap.CompressFormat.JPEG;
        }

        OutputStream outputStream = null;
        try {
            outputStream = context.getContentResolver().openOutputStream(outUri);
            bitmap.compress(outFormat, 100, outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();

            saveResult.isOK = false;
            return saveResult;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        saveResult.isOK = true;
        saveResult.uri = outUri;
        return saveResult;
    }

    public static BitmapDrawable createBitmapDrawable(Context context, Uri uri) throws IOException {
        BitmapDrawable bitmapDrawable = null;

        ContentResolver contentResolver = context.getContentResolver();
        ParcelFileDescriptor parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r");

        try {
            bitmapDrawable = createBitmapDrawable(context, parcelFileDescriptor.getFileDescriptor());
        } finally {
            if (parcelFileDescriptor != null) {
                parcelFileDescriptor.close();
            }
        }

        return bitmapDrawable;
    }

    public static BitmapDrawable createBitmapDrawable(Context context, FileDescriptor fileDescriptor) {
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor, null, getBitmapOption());
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);

        return drawable;
    }

    public static class SaveResult {
        public boolean isOK;
        public Uri uri;
    }
}
