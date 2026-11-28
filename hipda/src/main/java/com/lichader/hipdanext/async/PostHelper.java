package com.lichader.hipdanext.async;

/** The post modes, and the wait between posts. Posts are sent by {@link PostSender}. */
public class PostHelper {

    public static final int MODE_REPLY_THREAD = 0;
    public static final int MODE_REPLY_POST = 1;
    public static final int MODE_QUOTE_POST = 2;
    public static final int MODE_NEW_THREAD = 3;
    public static final int MODE_QUICK_REPLY = 4;
    public static final int MODE_EDIT_POST = 5;
    public static final int MODE_QUICK_DELETE = 6;
    public static final int MODE_VOTE_POLL = 7;

    private static long LAST_POST_TIME = 0;
    private static final long POST_DELAY_IN_SECS = 30;

    /** Starts the wait before the next post. */
    public static void markPosted() {
        LAST_POST_TIME = System.currentTimeMillis();
    }

    public static int getWaitTimeToPost() {
        long delta = (System.currentTimeMillis() - LAST_POST_TIME) / 1000;
        if (POST_DELAY_IN_SECS > delta) {
            return (int) (POST_DELAY_IN_SECS - delta);
        }
        return 0;
    }
}
