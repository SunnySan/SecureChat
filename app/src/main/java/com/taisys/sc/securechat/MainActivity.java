package com.taisys.sc.securechat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.taisys.oti.Card;
import com.taisys.oti.Card.SCSupported;
import com.taisys.sc.securechat.Application.App;
import com.taisys.sc.securechat.util.Utility;

import kr.co.namee.permissiongen.PermissionGen;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SecureChat";

    private Card mCard = new Card();
    private ProgressDialog pg = null;
    private Context myContext = null;
    private String iccid = null;

    //Firebase
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myContext = this;

        //Firebase
        mAuth = FirebaseAuth.getInstance();

        Utility.setMySetting(myContext, "iccid", "");   //先把程式裡的 iccid 設定清除
        setOnClickListener();

        PermissionGen.with(this)
                .addRequestCode(100)
                .permissions(
                        Manifest.permission.READ_PHONE_STATE
                        , Manifest.permission.READ_SMS
                        , Manifest.permission.SEND_SMS
                        , Manifest.permission.READ_CONTACTS
                        , Manifest.permission.WRITE_CONTACTS
                        , Manifest.permission.RECEIVE_SMS
                        , Manifest.permission.ACCESS_NETWORK_STATE
                        , Manifest.permission.READ_EXTERNAL_STORAGE
                        , Manifest.permission.WRITE_EXTERNAL_STORAGE
                        , android.Manifest.permission.RECORD_AUDIO
                        , android.Manifest.permission.WAKE_LOCK
                        , Manifest.permission.MODIFY_AUDIO_SETTINGS
                        , Manifest.permission.VIBRATE
                        , Manifest.permission.CAMERA
                        , Manifest.permission.READ_LOGS
                )
                .request();

    }

    @Override
    public void onDestroy() {
        if (mCard!=null){
            mCard.CloseSEService();
        }
        Log.d(TAG, "destroy Linphone...");
        App.getLinphoneManager().destroy(); //把VoIP關掉

        Utility.showToast(myContext, "clean data...");
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        cleanDataAndExitApp();
    }

    private void showWaiting(final String title, final String msg) {
        disWaiting();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pg = new ProgressDialog(myContext);
                // }
                pg.setIndeterminate(true);
                pg.setCancelable(false);
                pg.setCanceledOnTouchOutside(false);
                pg.setTitle(title);
                pg.setMessage(msg);
                pg.show();
            }
        });
    }

    private void disWaiting() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pg != null && pg.isShowing()) {
                    pg.dismiss();
                }
            }
        });
    }

    private void setOnClickListener(){
        Button b1 = (Button) findViewById(R.id.btnMainConfirm);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //顯示Progress對話視窗
                showWaiting(getString(R.string.msgPleaseWait), getString(R.string.msgVerifyPinCode));
                mCard.OpenSEService(myContext, "A000000018506373697A672D63617264",
                        new SCSupported() {

                            @Override
                            public void isSupported(boolean success) {
                                if (success) {
                                    //手機支援OTI
                                    getCardInfo();
                                } else {
                                    disWaiting();
                                    Utility.showMessage(myContext, getString(R.string.msgDoesntSupportOti));
                                }
                            }
                        });
            }
        });

        Button b2 = (Button) findViewById(R.id.btnMainCancel);
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cleanDataAndExitApp();
            }
        });
    }

    private void getCardInfo(){
        String res[] = mCard.GetCardInfo();
        String s = "";
        int i = 0;
        int j = 0;

        if (res != null && res[0].equals(Card.RES_OK)) {
            /*
                            res[1] 的結構如下：
                            假设拿到回复信息为：040001C3D908123456789012345601010412000100
                            其中   040001C3D9           LV结构 04长度，0001C3D9文件系统剩余空间大小，0x0001C3D9 = 115673 byte；
                            081234567890123456   LV结构 08长度，081234567890123456为卡号；
                            0101                 LV结构 01长度，01卡片版本号；
                            0412000100           LV结构 04长度，12000100 Cos版本号；
                         */
            s = res[1].substring(0, 2);
            i = Integer.parseInt(s);
            s = res[1].substring((i+1)*2, (i+1)*2 + 2);
            //utility.showMessage(myContext, s);
            j = Integer.parseInt(s);
            iccid = res[1].substring((i+1)*2+2, (i+1)*2+2 + j*2);
            //iccid="ted";
            //utility.showMessage(myContext, s);
            //i = s.length();
            //utility.showMessage(myContext, String.valueOf(i));
            Utility.setMySetting(myContext, "iccid", iccid);
            //utility.showToast(this, "CardInfor: " + res[1]);
            //utility.showToast(this, "ICCID= " + iccid);
            verifyPinCode();
        } else {
            disWaiting();
            Utility.showMessage(myContext, getString(R.string.msgCannotReadCardInfo));
        }

    }

    private void verifyPinCode(){
        EditText editTextPinCode = (EditText) findViewById(R.id.editTextMainPinCode);
        String pinCode = editTextPinCode.getText().toString();
        if (pinCode.length()==0){
            disWaiting();
            Utility.showMessage(myContext,getString(R.string.labelPleaseEnterPinCode));
            return;
        }
        //utility.showToast(myContext, getString(R.string.msgVerifyPinCode));
        //showWaiting(getString(R.string.pleaseWait), getString(R.string.msgVerifyPinCode));
        int pinId = 0x1;
        pinCode = Utility.byte2Hex(pinCode.getBytes());
        String res = mCard.VerifyPIN(pinId, pinCode);
        if (mCard!=null){
            mCard.CloseSEService();
        }
        disWaiting();
        if (res != null && res.equals(Card.RES_OK)) {
            Log.d(TAG, "PIN verification passed");
            //登入firebase看看是否成功，若成功表示此SIM卡已註冊過firebase帳號，若失敗則讓用戶註冊firebase帳號
            loginFirebase();
        } else {
            Log.d(TAG, "PIN code compared failed, user enter PIN= " + pinCode);
            Utility.showMessage(myContext, getString(R.string.msgPinCodeIsIncorrect));
        }
    }

    //登入firebase看看是否成功，若成功表示此SIM卡已註冊過firebase帳號，若失敗則讓用戶註冊firebase帳號
    private void loginFirebase(){
        String email = iccid + "@gmail.com";
        String password = "111111";

        showWaiting(getString(R.string.msgPleaseWait), getString(R.string.msgValidatingYourAccount));

        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                disWaiting();
                if(!task.isSuccessful()){
                    goToSignUpActivity();
                }else{
                    goToChatUsersActivity();
                }
            }
        });
    }

    private void goToSignUpActivity(){
        startActivity(new Intent(this, SignUpActivity.class));
    }

    private void goToChatUsersActivity(){
        startActivity(new Intent(this, ChatUsersActivity.class));
    }

    private void cleanDataAndExitApp(){
        Log.d(TAG, "About to leave APP...");
        if (mCard!=null){
            mCard.CloseSEService();
        }
        //Utility.showToast(myContext, "clean data...");
        //finish();
        System.exit(0); //將程式結束，下次進來需重新輸入PIN code做驗證

    }








}
