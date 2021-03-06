package com.daniels.harry.assignment.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

import com.daniels.harry.assignment.R;
import com.daniels.harry.assignment.activity.FavouritePickerActivity;

public class ConfirmDialogs {

    // confirmation dialogs to be used throughout the app

    public static void showConfirmFavouriteDialog(Context c, DialogInterface.OnClickListener listener, String teamName) {
        new AlertDialog.Builder(c)
                .setTitle(c.getString(R.string.dialog_title_confirm))
                .setMessage(c.getString(R.string.dialog_message_team_confirm, teamName))
                .setPositiveButton(android.R.string.yes, listener)
                .setNegativeButton(android.R.string.no, null).show();
    }
    
    public static void showConfirmSignOutDialog(Context c, DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(c)
                .setTitle(c.getString(R.string.dialog_title_confirm))
                .setMessage(c.getString(R.string.sign_out_message))
                .setPositiveButton(android.R.string.yes, listener)
                .setNegativeButton(android.R.string.no, null).show();
    }
}
