package com.lichader.hipdanext.cache;

/**
 * downloaded image cache
 */
public class ImageContainer {

    private static LRUCache<String, ImageInfo> IMAGES = new LRUCache<>(1024);

    public static void markImageReady(String url, ImageInfo imageInfo) {
        IMAGES.put(url, imageInfo);
    }

    public static void markImageIdle(String url) {
        if (IMAGES.containsKey(url)) {
            IMAGES.get(url).setStatus(ImageInfo.IDLE);
        }
    }

    public static ImageInfo getImageInfo(String url) {
        ImageInfo imageInfo = IMAGES.get(url);
        if (imageInfo == null) {
            imageInfo = new ImageInfo(url);
            IMAGES.put(url, imageInfo);
        }
        return imageInfo;
    }

}
