package com.taisys.sc.securechat.Application;

import android.app.Application;
import android.content.Context;

import com.google.firebase.database.FirebaseDatabase;
import com.taisys.sc.securechat.util.LinphoneManager;

/**
 * Created by sunny.sun on 2018/1/10.
 */

public class App extends Application {
    private static Context mContext;    //為了讓 MessageAdapter.java 可以取用 strings.xml 裡面的字串

    private static LinphoneManager mLinphoneManager;

    @Override
    public void onCreate() {
        super.onCreate();
        //set database to persist
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

        mContext = this;

        mLinphoneManager = new LinphoneManager(this);
    }

    public static Context getContext(){
        return mContext;
    }

    public static LinphoneManager getLinphoneManager(){
        return mLinphoneManager;
    }

    public void setLinphoneManager(LinphoneManager mLinphoneManager){
        this.mLinphoneManager = mLinphoneManager;
    }

}
