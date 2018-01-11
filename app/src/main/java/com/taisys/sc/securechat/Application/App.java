package com.taisys.sc.securechat.Application;

import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;

/**
 * Created by sunny.sun on 2018/1/10.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //set database to persist
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
