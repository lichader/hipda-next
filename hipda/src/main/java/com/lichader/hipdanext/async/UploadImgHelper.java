package com.lichader.hipdanext.async;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.text.TextUtils;

import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.forum.post.ImageUpload;
import com.lichader.hipdanext.ui.ForumErrorFormatter;
import com.lichader.hipdanext.utils.CursorUtils;
import com.lichader.hipdanext.utils.ImageFileInfo;
import com.lichader.hipdanext.utils.Logger;
import com.lichader.hipdanext.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.hilt.android.qualifiers.ApplicationContext;
import id.zelory.compressor.Compressor;

public class UploadImgHelper {

    private final static int MAX_QUALITY = 80;
    private final static int MAX_DIMENSION = 2560;
    private static final int THUMB_SIZE = 256;

    private int mMaxUploadSize = 2048 * 1024;

    private final Context mContext;
    private final EditorBridge mEditorBridge;
    private final ForumErrorFormatter mErrors;

    private UploadImgListener mListener;

    private String mUid;
    private String mHash;
    private Uri[] mUris;
    private boolean mOriginal;

    private String mMessage = "";
    private String mDetail = "";
    private Bitmap mThumb;
    private int mTotal;
    private int mCurrent;

    private File mTempFile;
    private File mCompressedFile;

    /** Creates an {@link UploadImgHelper} for one batch of images. */
    @AssistedFactory
    public interface Factory {
        UploadImgHelper create(UploadImgListener listener,
                               @Assisted("uid") String uid,
                               @Assisted("hash") String hash,
                               Uri[] uris,
                               boolean original);
    }

    @AssistedInject
    public UploadImgHelper(@ApplicationContext Context context,
                           HiSettingsHelper settings,
                           EditorBridge editorBridge,
                           ForumErrorFormatter errors,
                           @Assisted UploadImgListener v,
                           @Assisted("uid") String uid,
                           @Assisted("hash") String hash,
                           @Assisted Uri[] uris,
                           @Assisted boolean original) {
        mContext = context;
        mEditorBridge = editorBridge;
        mErrors = errors;
        mListener = v;
        mUid = uid;
        mHash = hash;
        mUris = uris;
        mOriginal = original;

        int maxUploadSize = settings.getMaxUploadFileSize();
        if (maxUploadSize > 0) {
            mMaxUploadSize = maxUploadSize;
        }
    }

    public interface UploadImgListener {
        void updateProgress(int total, int current, int percentage);

        void itemComplete(Uri uri, int total, int current, String message, String detail, String imgId, Bitmap thumbtail);
    }

    public void upload() {
        mTotal = mUris.length;

        int i = 0;
        for (Uri uri : mUris) {
            mCurrent = i++;
            mListener.updateProgress(mTotal, mCurrent, -1);
            String imgId = uploadImage(uri);
            mListener.itemComplete(uri, mTotal, mCurrent, mMessage, mDetail, imgId, mThumb);
        }
    }

    private static String getRandonTempFilename(Uri uri) {
        try {
            return Utils.md5(uri.toString() + System.currentTimeMillis());
        } catch (Exception e) {
            return System.currentTimeMillis() + "";
        }
    }

    private String uploadImage(Uri uri) {
        mThumb = null;
        mMessage = "";
        mTempFile = new File(Utils.getUploadDir() + File.separator + getRandonTempFilename(uri));
        try {
            Utils.copy(uri, mTempFile);
        } catch (Exception e) {
            mMessage = "无法读取选择的图片文件";
            mDetail = "\n" + uri.toString()
                    + "\n" + Utils.getStackTrace(e);
            Logger.e(e);
            return null;
        }

        ImageFileInfo imageFileInfo = CursorUtils.getImageFileInfo(mTempFile);
        if (imageFileInfo == null) {
            mMessage = "无法解析图片信息";
            mDetail = "\n" + uri.toString();
            return null;
        }

        if (imageFileInfo.isGif()
                && imageFileInfo.getFileSize() > mMaxUploadSize) {
            mMessage = "GIF图片大小不能超过" + Utils.toSizeText(mMaxUploadSize);
            return null;
        }

        ByteArrayOutputStream baos;
        try {
            Bitmap bitmap;
            if (isDirectUploadable(imageFileInfo)) {
                baos = readFileToStream(imageFileInfo.getFilePath());
                bitmap = BitmapFactory.decodeFile(imageFileInfo.getFilePath());
            } else {
                mCompressedFile = compressImageFile(imageFileInfo);
                if (mCompressedFile == null) {
                    mMessage = "无法压缩图片至指定大小 " + Utils.toSizeText(mMaxUploadSize);
                    mDetail = "\n" + uri.toString()
                            + "\n文件类型 : " + imageFileInfo.getMime()
                            + "\n原始大小 : " + Utils.toSizeText(imageFileInfo.getFileSize());
                    return null;
                }
                baos = readFileToStream(mCompressedFile.getAbsolutePath());
                bitmap = BitmapFactory.decodeFile(mCompressedFile.getAbsolutePath());
            }
            mThumb = ThumbnailUtils.extractThumbnail(bitmap, THUMB_SIZE, THUMB_SIZE);
            bitmap.recycle();
        } catch (Exception e) {
            mMessage = "处理图片发生错误";
            mDetail = "\n" + uri.toString()
                    + "\n" + Utils.getStackTrace(e);
            return null;
        }

        if (baos == null) {
            if (TextUtils.isEmpty(mMessage)) {
                mMessage = "处理图片发生错误2";
                mDetail = "\n" + uri.toString();
            }
            return null;
        }

        String imgId = null;
        try {
            imgId = postImage(imageFileInfo, baos);
        } catch (Exception e) {
            Logger.e(e);
            mMessage = mErrors.format(e);
            mDetail = "原图限制：" + Utils.toSizeText(mMaxUploadSize)
                    + "\n实际大小：" + Utils.toSizeText(baos.size())
                    + "\n" + e.getMessage();
        } finally {
            try {
                baos.close();
                if (mTempFile != null && mTempFile.exists())
                    mTempFile.delete();
                if (mCompressedFile != null && mCompressedFile.exists())
                    mCompressedFile.delete();
            } catch (IOException ignored) {
            }
        }
        return imgId;
    }

    private String postImage(ImageFileInfo imageFileInfo, ByteArrayOutputStream baos) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd_HHmmss", Locale.US);
        String fileName = "Hi" + (mCompressedFile != null ? "_" : "-") + formatter.format(new Date()) + "." + Utils.getImageFileSuffix(imageFileInfo.getMime());
        ImageUpload upload = mEditorBridge.uploadImage(mUid, mHash, fileName, imageFileInfo.getMime(), baos.toByteArray());
        if (upload instanceof ImageUpload.Uploaded) {
            return ((ImageUpload.Uploaded) upload).getImageId();
        }
        String response;
        if (upload instanceof ImageUpload.NoImageId) {
            mMessage = "无效上传图片ID";
            response = ((ImageUpload.NoImageId) upload).getResponse();
        } else {
            mMessage = "无法获取图片ID";
            response = ((ImageUpload.Unrecognized) upload).getResponse();
        }
        mDetail = "原图限制：" + Utils.toSizeText(mMaxUploadSize)
                + "\n实际大小：" + Utils.toSizeText(baos.size())
                + "\n" + response;
        return null;
    }

    private File compressImageFile(ImageFileInfo imageFileInfo) throws Exception {
        int maxDimension = Math.max(imageFileInfo.getWidth(), imageFileInfo.getHeight());
        maxDimension = Math.min(maxDimension, MAX_DIMENSION);
        String compressedFilename = getRandonTempFilename(Uri.fromFile(mTempFile));
        for (int i = 0; i < 5; i++) {
            int dimension = (int) (maxDimension * (5 - i) * 0.1);
            File compressedFile = new Compressor(mContext)
                    .setMaxWidth(dimension)
                    .setMaxHeight(dimension)
                    .setQuality(MAX_QUALITY)
                    .setCompressFormat(CompressFormat.JPEG)
                    .setDestinationDirectoryPath(Utils.getUploadDir().getAbsolutePath())
                    .compressToFile(mTempFile, compressedFilename);
            if (compressedFile.length() <= mMaxUploadSize) {
                return compressedFile;
            }
        }
        return null;
    }

    private boolean isDirectUploadable(ImageFileInfo imageFileInfo) {
        int w = imageFileInfo.getWidth();
        int h = imageFileInfo.getHeight();

        //原图/gif/超长图
        return (mOriginal || imageFileInfo.isGif() || (Math.max(w, h) * 1.0 / Math.min(w, h) >= 3))
                && imageFileInfo.getOrientation() <= 0
                && isMimeSupported(imageFileInfo.getMime())
                && imageFileInfo.getFileSize() <= mMaxUploadSize;
    }

    private boolean isMimeSupported(String mime) {
        return !TextUtils.isEmpty(mime)
                && (mime.contains("jpg")
                || mime.contains("jpeg")
                || mime.contains("png")
                || mime.contains("gif")
                || mime.contains("bmp"));
    }

    private static ByteArrayOutputStream readFileToStream(String file) {
        FileInputStream fileInputStream = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            fileInputStream = new FileInputStream(file);
            int readedBytes;
            byte[] buf = new byte[1024];
            while ((readedBytes = fileInputStream.read(buf)) > 0) {
                bos.write(buf, 0, readedBytes);
            }
            return bos;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (fileInputStream != null)
                    fileInputStream.close();
            } catch (Exception ignored) {

            }
        }
    }

}
