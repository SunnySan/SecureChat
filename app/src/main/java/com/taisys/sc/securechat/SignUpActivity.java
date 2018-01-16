package com.taisys.sc.securechat;

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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.taisys.oti.Card;
import com.taisys.oti.Card.SCSupported;
import com.taisys.sc.securechat.model.User;
import com.taisys.sc.securechat.util.Utility;

public class SignUpActivity extends AppCompatActivity {
    private Card mCard = new Card();
    private ProgressDialog pg = null;
    private Context myContext = null;
    private String iccid = null;

    //views
    private EditText mNameSignUpEditText;
    private Button mSignUpButton;
    private Button mCancelButton;

    //Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mUsersDBref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        //assign the views
        mNameSignUpEditText = (EditText)findViewById(R.id.editTextSignUpName);
        mSignUpButton = (Button)findViewById(R.id.btnSignUpConfirm);
        mCancelButton = (Button)findViewById(R.id.btnSignUpCancel);

        myContext = this;

        //firebase assign
        mAuth = FirebaseAuth.getInstance();

        iccid = Utility.getMySetting(myContext, "iccid");
        if (iccid==null || iccid.length()<1){
            Utility.showMessage(myContext, getString(R.string.msgUnableToGetIccid));
            finish();
        }

        setOnClickListener();

    }

    @Override
    public void onDestroy() {
        if (mCard!=null){
            mCard.CloseSEService();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        this.finish();
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
        mSignUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = mNameSignUpEditText.getText().toString();

                if(name.isEmpty()){
                    Utility.showMessage(myContext, getString(R.string.labelEnterYourName));
                    return;
                }

                //顯示Progress對話視窗
                showWaiting(getString(R.string.msgPleaseWait), getString(R.string.msgGeneratingRsaKey));
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

        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
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
            generateRsaKeyPair();
        } else {
            disWaiting();
            Utility.showMessage(myContext, getString(R.string.msgCannotReadCardInfo));
        }

    }











    private void generateRsaKeyPair(){
        String res[] = null;
        String s = "";
        int i = 0;
        int j = 0;

        long begintime = 0;

        //產生 RSA key pair
        begintime = System.currentTimeMillis();
        String resString = mCard.GenRSAKeyPair(Card.RSA_1024_BITS, 0x0201,
                0x0301);
        begintime = System.currentTimeMillis() - begintime;
        if (resString != null && resString.equals(Card.RES_OK)) {
            Log.d("SecureChat", "time:" + begintime + "ms, " + "Gen key pair OK!");
        } else {
            Log.d("SecureChat", "time:" + begintime + "ms, " + "Gen key pair Failed!");
            Utility.showMessage(myContext, getString(R.string.msgUnableToGenerateRsaKeyPair) + "error code=" + resString);
            disWaiting();
            return;
        }

        //讀出 public key
        String publicKey = "";
        res = mCard.ReadFile(0x0201, 0x0, 264);
        disWaiting();
        if (res != null && res[0].equals(Card.RES_OK)) {
            publicKey = res[1];
            Log.d("SecureChat", "public key=" + publicKey);
            String name = mNameSignUpEditText.getText().toString();
            String email = iccid + "@gmail.com";
            String password = "111111";
            signUpUserWithFirebase(name, email, password, publicKey);
        } else {
            Utility.showMessage(myContext, getString(R.string.msgFailToReadPublicKey) + "error code=" + res[0]);
            Log.d("SecureChat", "no public key:" + res[0]);
            return;
        }

        //順便先產生發送訊息時，要用 receiver public key 加密 3DES key 所需的 file，建立在 0x0202
        begintime = System.currentTimeMillis();
        resString = mCard
                .CreateFile(0x0202, (byte)0x02, 0x0, (byte)0x0, (byte)0x0, (byte)0x0);
        begintime = System.currentTimeMillis() - begintime;
        if (resString != null && resString.equals(Card.RES_OK)) {
            Log.d("SecureChat", "Create File success！ time=" + begintime + "ms");
        } else {
            Log.d("SecureChat", "Create File failed！ error code=" + resString);
        }

        /*
            res = mCard.ReadFile(0x0201, 4, 128);
            if (res != null && res[0].equals(Card.RES_OK)) {
                //Toast.makeText(SignUpActivity.this, "public key=" + res[1], Toast.LENGTH_SHORT).show();
                publicKey = res[1];
                Log.d("SecureChat", "public key=" + res[1]);
            } else {
                Toast.makeText(SignUpActivity.this, "no public key:" + res[0], Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "no public key:" + res[0]);
            }


            begintime = System.currentTimeMillis();
            resString = mCard
                    .CreateFile(0x0202, (byte)0x02, 0x0, (byte)0x0, (byte)0x0, (byte)0x0);
            begintime = System.currentTimeMillis() - begintime;
            if (resString != null && resString.equals(Card.RES_OK)) {
                Log.d("SecureChat", "Create File success！ time=" + begintime + "ms");
            } else {
                Log.d("SecureChat", "Create File failed！ error code=" + resString);
            }


            String[] res1 = mCard.ReadFile(0x0201, 0x0, 0xf7);
            String[] res2 = mCard.ReadFile(0x0201, 0xf7, 0x11);
            mCard.WriteFile(0x0202, 0, res1[1]);
            mCard.WriteFile(0x0202, 0xf7, res2[1]);

            String[] res1 = mCard.ReadFile(0x0201, 0x0, 264);
            mCard.WriteFile(0x0202, 0, res1[1]);

            begintime = System.currentTimeMillis();
            resString = mCard.WriteFile(0x0202, 0, res[1]);
            begintime = System.currentTimeMillis() - begintime;
            if (resString != null && resString.equals(Card.RES_OK)) {
                Log.d("SecureChat", "Write File success！ time=" + begintime + "ms");
            } else {
                Log.d("SecureChat", "Write File failed！ error code=" + resString);
            }


            res = mCard.ReadFile(0x0201, 4, 128);
            if (res != null && res[0].equals(Card.RES_OK)) {
                //Toast.makeText(SignUpActivity.this, "public key=" + res[1], Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "public key=" + res[1]);
            } else {
                Toast.makeText(SignUpActivity.this, "no public key:" + res[0], Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "no public key:" + res[0]);
            }

            begintime = System.currentTimeMillis();
            //String src = "test台灣宏碁testtesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttestt128testtesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttestt256test台灣宏碁testtesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttestt128testtesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttestt512";
            String src = "test台灣宏碁台灣宏碁test";
            byte[] srcBytes = stringToBytesUTFCustom(src);
            src = byte2Hex(srcBytes);
            //src = "7465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374";
            src = paddingString(src, 256);
            Log.d("SecureChat", "src=" + src);
            //Toast.makeText(SignUpActivity.this, "src=" + src, Toast.LENGTH_LONG).show();
            res = mCard.RSAPubKeyCalc(src, 0x0202);
            begintime = System.currentTimeMillis() - begintime;

            if (res != null && res[0].equals(Card.RES_OK)) {
                //Toast.makeText(SignUpActivity.this, "time:" + begintime + "ms\n" + res[1], Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "SIM card encrypt data successfully, time:" + begintime + "ms, " + res[1]);
                mNameSignUpEditText.setText(res[1]);


                byte[] encryptionBytes = doRSAEncryption(srcBytes, publicKey, "000000", "RSA/ECB/NoPadding", Cipher.ENCRYPT_MODE);
                if (encryptionBytes!=null && encryptionBytes.length>0) {
                    Log.d("SecureChat", "APP encrypt data successfully, bytes=:" + encryptionBytes);
                }else{
                    Log.d("SecureChat", "APP encrypt data failed");
                }


                begintime = System.currentTimeMillis();
                res = mCard.RSAPriKeyCalc(res[1], true, 0x0301);
                //res = mCard.RSAPriKeyCalc(byte2Hex(encryptionBytes), true, 0x0301);
                begintime = System.currentTimeMillis() - begintime;
                if (res != null && res[0].equals(Card.RES_OK)) {
                    Log.d("SecureChat", "decrypt data successfully, time:" + begintime + "ms, " + res[1]);
                    mNameSignUpEditText.setText(res[1]);
                    src = bytesToStringUTFCustom(hex2Byte(res[1]));
                    src = src.substring(0, getPlainTextLength(hex2Byte(res[1]))/2);
                    Log.d("SecureChat", "Source string=" + src);
                    mNameSignUpEditText.setText(src);
                } else {
                    Toast.makeText(SignUpActivity.this, "decrypt data failed, time:" + begintime + "ms\n" + "Error code: "
                            + res[0], Toast.LENGTH_SHORT).show();
                    Log.d("SecureChat", "decrypt data failed, time:" + begintime + "ms, error code=" + res[0]);
                }
            } else {
                Toast.makeText(SignUpActivity.this, "time:" + begintime + "ms\n" + "Error code: "
                        + res[0], Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "encrypt data failed, time:" + begintime + "ms, error code=" + res[0]);
            }

        } else {
            Toast.makeText(SignUpActivity.this, "Unable to get your id from SIM card, please make sure your thin SIM is correctly installed.", Toast.LENGTH_LONG).show();
        }
        */
    }

    private void signUpUserWithFirebase(final String name, String email, String password, final String publicKey){
        showWaiting(getString(R.string.msgPleaseWait), getString(R.string.menuAccountSignUp));

        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if(!task.isSuccessful()){
                    //there was an error
                    disWaiting();
                    Utility.showMessage(myContext, getString(R.string.msgFailToSignUpAccount) + " error= " + task.getException().getLocalizedMessage());
                    //Toast.makeText(SignUpActivity.this, "Error " + task.getException().getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }else{
                    final FirebaseUser newUser = task.getResult().getUser();
                    Log.d("SecureChat", "set firebase display name=" + name);
                    //success creating user, now set display name as name
                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();

                    newUser.updateProfile(profileUpdates)
                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    disWaiting();
                                    if (task.isSuccessful()) {
                                        Log.d("SecureChat", "User profile updated. Display name= " + newUser.getDisplayName());
                                        /***CREATE USER IN FIREBASE DB AND REDIRECT ON SUCCESS**/
                                        createUserInDb(newUser.getUid(), newUser.getDisplayName(), newUser.getEmail(), publicKey);

                                    }else{
                                        //error
                                        disWaiting();
                                        Utility.showMessage(myContext, getString(R.string.msgFailToSignUpAccount) + " error= " + task.getException().getLocalizedMessage());
                                        //Toast.makeText(SignUpActivity.this, "Error " + task.getException().getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                }
                            });

                }
            }
        });
    }

    private void createUserInDb(String userId, final String displayName, String email, String publicKey){
        mUsersDBref = FirebaseDatabase.getInstance().getReference().child("Users");
        User user = new User(userId, displayName, email, publicKey);
        mUsersDBref.child(userId).setValue(user).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if(!task.isSuccessful()){
                    //error
                    disWaiting();
                    Utility.showMessage(myContext, getString(R.string.msgFailToSignUpAccount) + " error= " + task.getException().getLocalizedMessage());
                    //Toast.makeText(SignUpActivity.this, "Error " + task.getException().getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    return;
                }else{
                    //success adding user to db as well
                    //go to users chat list
                    goToChartUsersActivity();
                }
            }
        });
    }

    private void goToChartUsersActivity(){
        startActivity(new Intent(this, ChatUsersActivity.class));
        finish();
    }

}
