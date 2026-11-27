package com.lichader.hipdanext.okhttp;

public interface ProgressListener {
    void update(String url, long bytesRead, long contentLength, boolean done);
}
