package com.taisys.sc.securechat;

import android.app.ProgressDialog;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.taisys.oti.Card;
import com.taisys.sc.securechat.util.Utility;

public class ChangePinCodeActivity extends AppCompatActivity {
    private static final String TAG = "SecureChat";

    private Card mCard = new Card();
    private ProgressDialog pg = null;
    private Context myContext = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_pin_code);

        myContext = this;
        setOnClickListener();
    }

    @Override
    public void onDestroy() {
        if (mCard!=null){
            mCard.CloseSEService();
        }
        super.onDestroy();
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

    @Override
    public void onBackPressed() {
        this.finish();
    }

    private void setOnClickListener(){
        Button b1 = (Button) findViewById(R.id.buttonChangePinCodeConfirm);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //顯示Progress對話視窗
                // showWaiting(getString(R.string.pleaseWait), getString(R.string.msgCheckCardAvailability));
                showWaiting(getString(R.string.msgPleaseWait), getString(R.string.msgChangePinCodeInProgress));
                mCard.OpenSEService(myContext, "A000000018506373697A672D63617264",
                        new Card.SCSupported() {

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

        Button b2 = (Button) findViewById(R.id.buttonChangePinCodeCancel);
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void getCardInfo(){
        //顯示Progress對話視窗
        //utility.showToast(myContext, getString(R.string.msgReadCardInfo));
        //showWaiting(getString(R.string.pleaseWait), getString(R.string.msgReadCardInfo));
        Log.d(TAG, "Get Card Info");
        String res[] = mCard.GetCardInfo();
        //disWaiting();
        if (res != null && res[0].equals(Card.RES_OK)) {
            changePinCode();
        } else {
            disWaiting();
            Utility.showMessage(myContext, getString(R.string.msgUnableToGetIccid));
        }

    }

    private void changePinCode(){
        Log.d(TAG, "Change PIN code");
        EditText editTextPinCode = (EditText) findViewById(R.id.editTextChangePinCodeCurrentPinCode);
        String oldPinCode = editTextPinCode.getText().toString();
        if (oldPinCode.length()==0){
            disWaiting();
            Utility.showMessage(myContext,getString(R.string.labelPleaseEnterPinCode));
            return;
        }

        EditText editTextNewPinCode = (EditText) findViewById(R.id.editTextChangePinCodeNewPinCode);
        String newPinCode = editTextNewPinCode.getText().toString();
        if (newPinCode.length()==0){
            disWaiting();
            Utility.showMessage(myContext,getString(R.string.msgPleaseEnterNewPinCode));
            return;
        }
        if (newPinCode.length()<6){
            disWaiting();
            Utility.showMessage(myContext,getString(R.string.msgNewPinCodeIsTooShort));
            return;
        }

        EditText editTextNewPinCode2 = (EditText) findViewById(R.id.editTextChangePinCodeNewPinCode2);
        String newPinCode2 = editTextNewPinCode2.getText().toString();
        if (newPinCode2.length()==0){
            disWaiting();
            Utility.showMessage(myContext,getString(R.string.msgPleaseEnterNewPinCodeAgain));
            return;
        }
        if (!newPinCode.equals(newPinCode2)){
            disWaiting();
            Utility.showMessage(myContext,getString(R.string.msgNewPinCodesAreNotTheSame));
            return;
        }
        //utility.showToast(myContext, getString(R.string.msgVerifyPinCode));
        //showWaiting(getString(R.string.pleaseWait), getString(R.string.msgVerifyPinCode));
        int pinId = 0x1;
        oldPinCode = Utility.byte2Hex(oldPinCode.getBytes());
        newPinCode = Utility.byte2Hex(newPinCode.getBytes());
        String res = mCard.ChangePIN(pinId, oldPinCode, newPinCode);
        disWaiting();
        if (mCard!=null){
            mCard.CloseSEService();
        }
        if (res != null && res.equals(Card.RES_OK)) {
            Log.d(TAG, "Change PIN code successfully.");
            Utility.showMessage(myContext, getString(R.string.msgSuccess));
        } else {
            Log.d(TAG, "PIN code compared failed, user enter PIN= " + newPinCode + ", response= " + res);
            //utility.showMessage(myContext, pinCode);
            Utility.showMessage(myContext, getString(R.string.msgProcessFailed));
        }
    }



}
