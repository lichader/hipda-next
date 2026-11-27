package com.lichader.hipdanext.okhttp;

public class NetworkError {
    private String message;
    private String detail;
    private int errCode;

    NetworkError(int errCode, String message, String detail) {
        this.message = message;
        this.detail = detail;
        this.errCode = errCode;
    }


    public String getDetail() {
        return detail;
    }

    public String getMessage() {
        return message;
    }

    public int getErrCode() {
        return errCode;
    }

    @Override
    public String toString() {
        return message;
    }
}
