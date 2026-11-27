package com.lichader.hipdanext.cache;

import com.lichader.hipdanext.utils.Utils;

public class SignatureContainer {

    private static LRUCache<String, String> SIGS = new LRUCache<>(128);

    public static void putSignature(String uid, String signature) {
        SIGS.put(uid, signature);
    }

    public static String getSignature(String uid) {
        return Utils.nullToText(SIGS.get(uid));
    }


}
