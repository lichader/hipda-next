package com.lichader.hipdanext.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class ContentDBHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "content.db";
    public static final String TABLE_NAME = "Contents";

    private static final String CONTENTS_TABLE_CREATE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    "session_id TEXT PRIMARY KEY, " +
                    "username TEXT," +
                    "time NUMERIC, " +
                    "content TEXT);";

    @Inject
    public ContentDBHelper(@ApplicationContext Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CONTENTS_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion > oldVersion) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN username TEXT");
        }
    }
}
