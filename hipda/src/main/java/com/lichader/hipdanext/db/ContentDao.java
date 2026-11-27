package com.lichader.hipdanext.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.utils.Logger;

import java.util.LinkedHashSet;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Created by GreenSkinMonster on 2016-07-23.
 */
@Singleton
public class ContentDao {

    private final static int MAX_SIZE = 10;

    private final ContentDBHelper mDbHelper;
    private final HiSettingsHelper mSettings;

    @Inject
    public ContentDao(ContentDBHelper dbHelper, HiSettingsHelper settings) {
        mDbHelper = dbHelper;
        mSettings = settings;
    }

    public synchronized void saveContent(String sessionId, String content) {
        try (SQLiteDatabase db = mDbHelper.getWritableDatabase()) {
            if (!TextUtils.isEmpty(content)) {
                ContentValues contentValues = new ContentValues();
                contentValues.put("session_id", sessionId);
                contentValues.put("username", mSettings.getUsername());
                contentValues.put("time", System.currentTimeMillis());
                contentValues.put("content", content);
                db.replace(ContentDBHelper.TABLE_NAME, null, contentValues);
            } else {
                db.delete(ContentDBHelper.TABLE_NAME, "session_id=?", new String[]{sessionId});
            }
        } catch (Exception e) {
            Logger.e(e);
        }
    }

    public synchronized void cleanup() {
        try (SQLiteDatabase db = mDbHelper.getWritableDatabase()) {
            db.execSQL("delete from " + ContentDBHelper.TABLE_NAME
                    + " where session_id not in " +
                    "(select session_id from " + ContentDBHelper.TABLE_NAME +
                    " order by time desc limit " + MAX_SIZE + ")" +
                    " or session_id is null" +
                    " or session_id=''");
        } catch (Exception e) {
            Logger.e(e);
        }
    }

    public Content[] getSavedContents(String currentSessionId) {
        Content[] contents = null;
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = mDbHelper.getReadableDatabase();

            String[] projection = {
                    "username",
                    "session_id",
                    "time",
                    "content"
            };

            cursor = db.query(
                    ContentDBHelper.TABLE_NAME,
                    projection,
                    "content is not null and content<>'' and session_id<>?",
                    new String[]{currentSessionId},
                    null,
                    null,
                    "time desc",
                    "" + MAX_SIZE
            );

            LinkedHashSet<Content> cnts = new LinkedHashSet<>();
            while (cursor.moveToNext()) {
                String username = cursor.getString(cursor.getColumnIndex("username"));
                long time = cursor.getLong(cursor.getColumnIndex("time"));
                String sessionId = cursor.getString(cursor.getColumnIndex("session_id"));
                String content = cursor.getString(cursor.getColumnIndex("content"));

                cnts.add(new Content(sessionId, username, content, time));
            }

            contents = cnts.toArray(new Content[cnts.size()]);
        } catch (Exception e) {
            Logger.e(e);
        } finally {
            if (cursor != null)
                cursor.close();
            if (db != null)
                db.close();
        }
        return contents;
    }

}
