package com.lichader.hipdanext.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.graphics.Insets;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.snackbar.Snackbar;

import com.lichader.hipdanext.BuildConfig;
import com.lichader.hipdanext.R;
import com.lichader.hipdanext.async.FileDownTask;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.bean.Theme;
import com.lichader.hipdanext.cache.ImageContainer;
import com.lichader.hipdanext.cache.ImageInfo;
import com.lichader.hipdanext.di.SingletonEntryPoint;
import com.lichader.hipdanext.ui.HiApplication;
import com.lichader.hipdanext.ui.MainFrameActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class UIUtils {

    private final static String IMAGES_DIR = "HiPDA";

    public static void infoSnack(View view, CharSequence message) {
        if (view != null) {
            makeSnack(view, message, null, Snackbar.LENGTH_SHORT, Color.WHITE)
                    .show();
        }
    }

    public static void errorSnack(View view, CharSequence message, CharSequence detail) {
        if (view != null) {
            makeSnack(view, message, detail, Snackbar.LENGTH_LONG,
                    ContextCompat.getColor(HiApplication.getAppContext(), R.color.md_yellow_500))
                    .show();
        }
    }

    private static Snackbar makeSnack(final View view, final CharSequence message, final CharSequence detail, int length, int textColor) {
        final Snackbar snackbar = Snackbar.make(view, message, length);
        setSnackbarMessageTextColor(snackbar, textColor);

        if (!TextUtils.isEmpty(detail)) {
            snackbar.setAction("详情", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UIUtils.showMessageDialog(view.getContext(),
                            "详细信息",
                            message + "\n" + detail, true);
                    snackbar.dismiss();

                }
            });
        }
        return snackbar;
    }

    public static Snackbar makeSnackbar(View view, CharSequence sequence, int duration, int textColor) {
        Snackbar snackbar = Snackbar.make(view, sequence, duration);
        setSnackbarMessageTextColor(snackbar, textColor);
        return snackbar;
    }

    public static void setSnackbarMessageTextColor(Snackbar snackbar, int color) {
        View view = snackbar.getView();
        ((TextView) view.findViewById(R.id.snackbar_text)).setTextColor(color);
    }

    public static AlertDialog.Builder getMessageDialogBuilder(final Context context, String message, final String detail) {
        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View viewlayout = inflater.inflate(R.layout.item_simple_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        final TextView tvTitle = (TextView) viewlayout.findViewById(R.id.tv_dialog_title);
        tvTitle.setText(message);

        final TextView textView = (TextView) viewlayout.findViewById(R.id.tv_dialog_content);
        textView.setText(detail);
        // Reached from errorSnack() all over the app, so the settings are looked up here.
        UIUtils.setLineSpacing(textView, SingletonEntryPoint.get().settings());

        builder.setView(viewlayout);
        return builder;
    }

    public static void showMessageDialog(final Context context, String message, final String detail, boolean copyable) {
        AlertDialog.Builder builder = getMessageDialogBuilder(context, message, detail);
        builder.setPositiveButton(context.getResources().getString(R.string.action_close), null);
        if (copyable) {
            builder.setNeutralButton(context.getResources().getString(R.string.action_copy),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("COPY FROM HiPDA", detail);
                            clipboard.setPrimaryClip(clip);
                            UIUtils.toast("内容已复制");
                        }
                    });
        }
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static void showReleaseNotesDialog(final Activity activity) {
        String releaseNotes;
        try {
            releaseNotes = Utils.readFromAssets(activity, "release-notes.txt");
        } catch (Exception e) {
            releaseNotes = e.getMessage();
        }

        String info = "问题反馈请到 “设置-客户端发布帖”，务必提供系统版本和客户端版本信息。\n崩溃日志在“设置-其它-应用错误日志”中。\n\n";

        showMessageDialog(activity, "更新记录", info + releaseNotes, false);
    }

    /**
     * The Activity behind a context, unwrapping {@link ContextWrapper}s such as the one
     * Hilt gives {@code @AndroidEntryPoint} fragments and their views; null if none.
     */
    public static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity)
                return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static boolean askForStoragePermission(Context ctx) {
        // Scoped storage (API 29+): MediaStore and DownloadManager writes need no permission,
        // and WRITE_EXTERNAL_STORAGE is not requested there (maxSdkVersion 28).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return false;
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            UIUtils.toast("需要授予 \"存储空间\" 权限");
            Activity activity = findActivity(ctx);
            if (activity != null)
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MainFrameActivity.PERMISSIONS_REQUEST_CODE_STORAGE);
            return true;
        }
        return false;
    }

    public static void setLineSpacing(TextView textView, HiSettingsHelper settings) {
        float lineSpacingExtra = 2;
        float lineSpacingMultiplier = 1.1f;
        if (settings.getPostLineSpacing() == 1) {
            lineSpacingExtra = 4;
            lineSpacingMultiplier = 1.2f;
        } else if (settings.getPostLineSpacing() == 2) {
            lineSpacingExtra = 6;
            lineSpacingMultiplier = 1.3f;
        } else if (settings.getPostLineSpacing() == 3) {
            lineSpacingExtra = 8;
            lineSpacingMultiplier = 1.4f;
        }
        textView.setLineSpacing(lineSpacingExtra, lineSpacingMultiplier);
    }

    public static void hideSoftKeyboard(Activity activity) {
        if (activity != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        }
    }

    public static void shareImage(final Activity activity, final View view, String url) {
        if (activity == null || view == null)
            return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                && askForStoragePermission(activity))
            return;

        final ImageInfo imageInfo = ImageContainer.getImageInfo(url);
        if (!imageInfo.isSuccess()) {
            FileDownTask fileDownTask = new FileDownTask(activity) {
                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    if (imageInfo.isSuccess())
                        shareImage(activity, view, imageInfo);
                    else
                        errorSnack(view, "分享时发生错误", mException != null ? mException.getMessage() : "");
                }
            };
            fileDownTask.execute(url);
        } else {
            shareImage(activity, view, imageInfo);
        }
    }

    private static void shareImage(Activity activity, final View view, ImageInfo imageInfo) {
        try {
            String filename = Utils.getImageFileName(Constants.FILE_SHARE_PREFIX, imageInfo.getMime());
            File cacheDirectory = HiApplication.getAppContext().getExternalCacheDir();
            File destFile = new File(cacheDirectory, filename);
            Utils.copy(new File(imageInfo.getPath()), destFile);

            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(
                        activity,
                        BuildConfig.APPLICATION_ID + ".provider",
                        destFile);
            } else {
                uri = Uri.fromFile(destFile);
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setType(imageInfo.getMime());
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            activity.startActivity(Intent.createChooser(shareIntent, "分享图片"));
        } catch (Exception e) {
            Logger.e(e);
            errorSnack(view, "分享时发生错误", e.getMessage());
        }
    }

    public static void saveImage(final Activity activity, final View view, String url) {
        if (activity == null || view == null)
            return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && askForStoragePermission(activity))
            return;

        final ImageInfo imageInfo = ImageContainer.getImageInfo(url);
        if (!imageInfo.isSuccess()) {
            FileDownTask fileDownTask = new FileDownTask(activity) {
                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    if (imageInfo.isSuccess()) {
                        saveImage(activity, view, imageInfo);
                    } else {
                        errorSnack(view, "保存时发生错误", mException != null ? mException.getMessage() : "");
                    }
                }
            };
            fileDownTask.execute(url);
        } else {
            saveImage(activity, view, imageInfo);
        }
    }

    private static void saveImage(final Activity activity, final View view, final ImageInfo imageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageQ(activity, view, imageInfo);
            return;
        }
        try {
            String filename = Utils.getImageFileName("Hi_IMG", imageInfo.getMime());

            File imagesDir = getImagesDir();
            final File destFile = new File(imagesDir, filename);
            Utils.copy(new File(imageInfo.getPath()), destFile);
            MediaScannerConnection.scanFile(activity, new String[]{destFile.getPath()}, null, null);

            Uri destUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                destUri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", destFile);
            } else {
                destUri = Uri.fromFile(destFile);
            }
            snackViewSaveImage(activity, view, destUri, imageInfo.getMime());
        } catch (Exception e) {
            Logger.e(e);
            errorSnack(view, "保存文件时发生错误", e.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static void saveImageQ(final Activity activity, final View view, ImageInfo imageInfo) {
        ContentResolver resolver = activity.getContentResolver();
        Uri destUri = null;
        try {
            String filename = Utils.getImageFileName("Hi_IMG", imageInfo.getMime());
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, imageInfo.getMime());
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + IMAGES_DIR);
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1);
            destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (destUri == null)
                throw new IOException("无法创建图片文件");

            try (InputStream fis = new FileInputStream(imageInfo.getPath());
                 OutputStream fos = resolver.openOutputStream(destUri)) {
                if (fos == null)
                    throw new IOException("无法写入图片文件");
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
            }

            ContentValues published = new ContentValues();
            published.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(destUri, published, null, null);

            snackViewSaveImage(activity, view, destUri, imageInfo.getMime());
        } catch (Exception e) {
            Logger.e(e);
            if (destUri != null) {
                try {
                    resolver.delete(destUri, null, null);
                } catch (Exception ignored) {
                }
            }
            errorSnack(view, "保存文件时发生错误", e.getMessage());
        }
    }

    private static void snackViewSaveImage(Activity activity, View view, Uri destUri, String mime) {
        Snackbar snackbar = makeSnackbar(view, "文件已保存至 Pictures/" + IMAGES_DIR + " 目录",
                Snackbar.LENGTH_LONG, Color.WHITE);

        snackbar.setAction("查看", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    intent.setDataAndType(destUri, mime);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    errorSnack(view, "打开文件发生错误", e.getMessage());
                }
            }
        });
        snackbar.show();
    }

    private static File getImagesDir() throws IOException {
        String imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
                        + "/" + IMAGES_DIR;
        File destDir = new File(imagesDir);
        if (destDir.isFile())
            destDir.delete();
        if (!destDir.exists()) {
            if (!destDir.mkdirs())
                throw new IOException("不能创建目录 " + imagesDir);
        }
        return destDir;
    }

    public static Boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static View getSnackView(Activity activity) {
        if (activity != null)
            return activity.getWindow().getDecorView().getRootView().findViewById(R.id.main_frame_container);
        return null;
    }

    public static int getScreenWidth(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return metrics.widthPixels;
    }

    public static int getScreenHeight(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return metrics.heightPixels;
    }

    public static void toast(String text) {
        Toast.makeText(HiApplication.getAppContext(), text, Toast.LENGTH_SHORT).show();
    }

    public static int getRelativeTop(View myView, ViewGroup parentView) {
        Rect offsetViewBounds = new Rect();
        myView.getDrawingRect(offsetViewBounds);
        parentView.offsetDescendantRectToMyCoords(myView, offsetViewBounds);
        return offsetViewBounds.top;
    }

    public static void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) HiApplication.getAppContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("COPY FROM HiPDA", text);
        clipboard.setPrimaryClip(clip);
    }

    public static void trimChildMargins(@NonNull ViewGroup vg) {
        final int childCount = vg.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = vg.getChildAt(i);

            if (child instanceof ViewGroup) {
                trimChildMargins((ViewGroup) child);
            }

            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).leftMargin = 0;
            }
            child.setBackground(null);
            child.setPadding(0, 0, 0, 0);
        }
    }

    public static void setLightDarkThemeMode(HiSettingsHelper settings) {
        if (HiSettingsHelper.THEME_MODE_AUTO.equals(settings.getTheme())) {
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else if (HiSettingsHelper.THEME_MODE_LIGHT.equals(settings.getTheme())) {
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_YES);
        }
    }

    public static void setActivityTheme(Activity activity, HiSettingsHelper settings) {
        activity.setTheme(getThemeValue(activity, settings));

        Window window = activity.getWindow();
        View view = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isWhiteTheme(activity, settings)) {
                view.setSystemUiVisibility(view.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                view.setSystemUiVisibility(view.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
        if (settings.isNavBarColored()) {
            window.setNavigationBarColor(ColorHelper.getColorPrimary(activity));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (UIUtils.isWhiteTheme(activity, settings)) {
                    view.setSystemUiVisibility(view.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                } else {
                    view.setSystemUiVisibility(view.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                }
            }
        }
    }

    public static boolean isInLightThemeMode(Context context) {
        int currentNightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
                return true;
            case Configuration.UI_MODE_NIGHT_YES:
                return false;
        }
        return true;
    }

    public static boolean isInDarkThemeMode(Context context) {
        return !isInLightThemeMode(context);
    }

    public static int getToolbarTextColor(Context context, HiSettingsHelper settings) {
        return isWhiteTheme(context, settings) ? Color.BLACK : Color.WHITE;
    }

    public static boolean isWhiteTheme(Context context, HiSettingsHelper settings) {
        return UIUtils.isInLightThemeMode(context)
                && HiSettingsHelper.THEME_WHITE.equals(settings.getLightTheme());
    }

    public static int getThemeValue(Context context, HiSettingsHelper settings) {
        String theme = UIUtils.isInLightThemeMode(context) ? HiSettingsHelper.THEME_MODE_LIGHT : HiSettingsHelper.THEME_MODE_DARK;
        if (HiSettingsHelper.THEME_MODE_DARK.equals(theme)) {
            if (HiSettingsHelper.THEME_BLACK.equals(settings.getDarkTheme()))
                return R.style.ThemeBlack;
            return R.style.ThemeDark;
        } else {
            String lightTheme = settings.getLightTheme();
            for (Theme t : HiSettingsHelper.LIGHT_THEMES) {
                if (t.getName().equals(lightTheme))
                    return t.getThemeId();
            }
            settings.setTheme(HiSettingsHelper.THEME_MODE_LIGHT);
            settings.setLightTheme(HiSettingsHelper.THEME_WHITE);
            return R.style.ThemeLight_White;
        }
    }

    /**
     * Apps targeting API 35+ are drawn edge-to-edge on Android 15+, and the window no longer
     * resizes for the keyboard. Keep the previous layout by padding the content view with the
     * system bar, display cutout and keyboard insets, and paint the padding with the color the
     * status bar used to have.
     */
    public static void applySystemBarInsets(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM)
            return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null)
            return;
        TypedValue typedValue = new TypedValue();
        if (activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimaryDark, typedValue, true)
                && typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            content.setBackgroundColor(typedValue.data);
        }
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }

    public static void hideSystemUI(Activity activity) {
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    public static void showSystemUI(Activity activity) {
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

}