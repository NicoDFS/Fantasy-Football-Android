package com.daniels.harry.assignment.repository;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import com.daniels.harry.assignment.R;
import com.daniels.harry.assignment.listener.OnDbSaveAsyncListener;
import com.orm.SugarRecord;

import java.util.List;

public class DbSaveAllAsync<T extends SugarRecord> extends AsyncTask<Void, Integer, Boolean> {

    private OnDbSaveAsyncListener mListener;
    private List<T> mObjects;
    private String mTag;

    public DbSaveAllAsync(List<T> objects, OnDbSaveAsyncListener listener, String tag) {
        mObjects = objects;
        mListener = listener;
        mTag = tag;
    }

    // async method for saving multiple database entries at once
    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            T.saveInTx(mObjects);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean result) {
            if (result)
                mListener.onDbSaveSuccess(mTag);
            else
                mListener.onDbSaveFailure(mTag);
    }
}
