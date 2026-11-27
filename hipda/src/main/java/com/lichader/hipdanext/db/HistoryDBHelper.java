package com.lichader.hipdanext.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Created by GreenSkinMonster on 2016-07-23.
 */
@Singleton
public class HistoryDBHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "history.db";
    public static final String TABLE_NAME = "History";

    private static final String CONTENTS_TABLE_CREATE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    "tid TEXT PRIMARY KEY, " +
                    "fid TEXT, " +
                    "title TEXT, " +
                    "uid TEXT, " +
                    "username TEXT, " +
                    "post_time TEXT, " +
                    "visit_time NUMERIC); ";

    @Inject
    public HistoryDBHelper(@ApplicationContext Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CONTENTS_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {

    }
}
