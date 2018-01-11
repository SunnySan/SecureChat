package com.taisys.sc.securechat;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

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

public class SignUpActivity extends AppCompatActivity {

    //views
    private EditText mNameSignUpEditText;
    private EditText mEmailSignUpEditText;
    private EditText mPasswordEditText;
    private Button mSignUpButton;
    private Button mGoToLoginButton;

    //Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mUsersDBref;

    private ProgressDialog mDialog;

    private Card mCard = new Card();
    String iccid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        //assign the views
        mNameSignUpEditText = (EditText)findViewById(R.id.nameSignUpEditText);
        mEmailSignUpEditText = (EditText)findViewById(R.id.emailSignUpEditText);
        mPasswordEditText = (EditText)findViewById(R.id.passwordSignUpEditText);
        mSignUpButton = (Button)findViewById(R.id.signUpButton);
        mGoToLoginButton = (Button)findViewById(R.id.goToLogIn);

        //firebase assign
        mAuth = FirebaseAuth.getInstance();

        //dialog
        mDialog = new ProgressDialog(this);

        /**listen to sign up button click**/
        mSignUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = mNameSignUpEditText.getText().toString();
                String email = mEmailSignUpEditText.getText().toString().trim();
                String password = mPasswordEditText.getText().toString().trim();


                if(name.isEmpty()){
                    Toast.makeText(SignUpActivity.this, "Name cannot be empty!", Toast.LENGTH_SHORT).show();
                }else if(email.isEmpty()){
                    Toast.makeText(SignUpActivity.this, "Email cannot be empty!", Toast.LENGTH_SHORT).show();
                }else if(password.isEmpty()){
                    Toast.makeText(SignUpActivity.this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
                }else{
                    signUpUserWithFirebase(name, email, password);
                }
            }
        });

        /**listen to go to login button**/
        mGoToLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToLoginActivity();
            }
        });

        initCard();

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

    private void initCard(){
        //顯示Progress對話視窗
        //mDialog.setMessage("Please wait...");
        //mDialog.show();
        //if (1==1)return;
        //utility.showToast(myContext, getString(R.string.msgReadCardInfo));
        //showWaiting(getString(R.string.pleaseWait), getString(R.string.msgReadCardInfo));

        mCard.OpenSEService(this, "A000000018506373697A672D63617264",
                new SCSupported() {

                    @Override
                    public void isSupported(boolean success) {
                        if (!success) {
                            //手機不支援OTI
                            Toast.makeText(SignUpActivity.this, "Unable to read credential information from SIM card, please make sure your thin SIM is correctly installed.", Toast.LENGTH_LONG).show();
                            return;
                        }else{
                            //Toast.makeText(SignUpActivity.this, "OTI OK.", Toast.LENGTH_LONG).show();
                            getCardInfo();
                        }
                    }
                });

    }
    private void getCardInfo(){
        String res[] = mCard.GetCardInfo();
        String myIccid = "";
        String s = "";
        int i = 0;
        int j = 0;
        //disWaiting();
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
            myIccid = res[1].substring((i+1)*2+2, (i+1)*2+2 + j*2);
            //utility.showMessage(myContext, s);
            //i = s.length();
            //utility.showMessage(myContext, String.valueOf(i));
            iccid = myIccid;
            //utility.showToast(this, "CardInfor: " + res[1]);
            //utility.showToast(this, "ICCID= " + iccid);
            mEmailSignUpEditText.setText(iccid + "@gmail.com");

            long begintime = 0;

            /*
            begintime = System.currentTimeMillis();
            String resString = mCard.GenRSAKeyPair(Card.RSA_1024_BITS, 0x0201,
                    0x0301);
            begintime = System.currentTimeMillis() - begintime;
            if (resString != null && resString.equals(Card.RES_OK)) {
                Toast.makeText(SignUpActivity.this, "time:" + begintime + "ms\n" + "Gen key pair OK!", Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "time:" + begintime + "ms, " + "Gen key pair OK!");
            } else {
                Toast.makeText(SignUpActivity.this, "time:" + begintime + "ms\n" + "Gen key pair failed!", Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "time:" + begintime + "ms, " + "Gen key pair Failed!");
            }
            */

            res = mCard.ReadFile(0x0201, 4, 128);
            if (res != null && res[0].equals(Card.RES_OK)) {
                //Toast.makeText(SignUpActivity.this, "public key=" + res[1], Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "public key=" + res[1]);
            } else {
                Toast.makeText(SignUpActivity.this, "no public key:" + res[0], Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "no public key:" + res[0]);
            }
            res = mCard.ReadFile(0x0201, 132, 3);
            if (res != null && res[0].equals(Card.RES_OK)) {
                //Toast.makeText(SignUpActivity.this, "public key=" + res[1], Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "public key=" + res[1]);
            } else {
                Toast.makeText(SignUpActivity.this, "no public key:" + res[0], Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "no public key:" + res[0]);
            }

            begintime = System.currentTimeMillis();
            String src = "test台灣宏碁testtesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttestt128testtesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttesttest255";
            src = byte2Hex(stringToBytesUTFCustom(src));
            //src = "7465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374746573747465737474657374";
            src = paddingString(src, 256);
            Log.d("SecureChat", "src=" + src);
            //Toast.makeText(SignUpActivity.this, "src=" + src, Toast.LENGTH_LONG).show();
            res = mCard.RSAPubKeyCalc(src, 0x0201);
            begintime = System.currentTimeMillis() - begintime;

            if (res != null && res[0].equals(Card.RES_OK)) {
                //Toast.makeText(SignUpActivity.this, "time:" + begintime + "ms\n" + res[1], Toast.LENGTH_SHORT).show();
                Log.d("SecureChat", "encrypt data successfully, time:" + begintime + "ms, " + res[1]);
                mNameSignUpEditText.setText(res[1]);

                begintime = System.currentTimeMillis();
                res = mCard.RSAPriKeyCalc(res[1], true, 0x0301);
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
        //mDialog.dismiss();
    }

    private void signUpUserWithFirebase(final String name, String email, String password){
        mDialog.setMessage("Please wait...");
        mDialog.show();

        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if(!task.isSuccessful()){
                    //there was an error
                    mDialog.dismiss();
                    Toast.makeText(SignUpActivity.this, "Error " + task.getException().getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }else{
                    final FirebaseUser newUser = task.getResult().getUser();
                    //success creating user, now set display name as name
                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();

                    newUser.updateProfile(profileUpdates)
                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    mDialog.dismiss();
                                    if (task.isSuccessful()) {
                                        Log.d(SignUpActivity.class.getName(), "User profile updated.");
                                        /***CREATE USER IN FIREBASE DB AND REDIRECT ON SUCCESS**/
                                        createUserInDb(newUser.getUid(), newUser.getDisplayName(), newUser.getEmail());

                                    }else{
                                        //error
                                        mDialog.dismiss();
                                        Toast.makeText(SignUpActivity.this, "Error " + task.getException().getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                    }
                                }
                            });

                }
            }
        });
    }

    private void createUserInDb(String userId, String displayName, String email){
        mUsersDBref = FirebaseDatabase.getInstance().getReference().child("Users");
        User user = new User(userId, displayName, email);
        mUsersDBref.child(userId).setValue(user).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if(!task.isSuccessful()){
                    //error
                    mDialog.dismiss();
                    Toast.makeText(SignUpActivity.this, "Error " + task.getException().getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }else{
                    //success adding user to db as well
                    //go to users chat list
                    goToChartUsersActivity();
                }
            }
        });
    }

    private void goToLoginActivity(){
        startActivity(new Intent(this, LoginActivity.class));
    }

    private void goToChartUsersActivity(){
        startActivity(new Intent(this, ChatUsersActivity.class));
        finish();
    }

/*************************************************************************************/
//取得 byte array 每個 byte 的 16 進位碼
public static String byte2Hex(byte[] b) {
    String result = "";
    for (int i=0 ; i<b.length ; i++)
        result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
    return result;
}

//將 16 進位碼的字串轉為 byte array
public static byte[] hex2Byte(String hexString) {
    byte[] bytes = new byte[hexString.length() / 2];
    for (int i=0 ; i<bytes.length ; i++)
        bytes[i] = (byte) Integer.parseInt(hexString.substring(2 * i, 2 * i + 2), 16);
    return bytes;
}

//將 byte array 轉成一個個 char 的字串
public static String bytesToStringUTFCustom(byte[] bytes) {
    char[] buffer = new char[bytes.length >> 1];
    for(int i = 0; i < buffer.length; i++) {
        int bpos = i << 1;
        char c = (char)(((bytes[bpos]&0x00FF)<<8) + (bytes[bpos+1]&0x00FF));
        buffer[i] = c;
    }
    return new String(buffer);
}

//將一個個 char 的字串轉成 byte array
public static byte[] stringToBytesUTFCustom(String str) {
    char[] buffer = str.toCharArray();
    byte[] b = new byte[buffer.length << 1];
    for(int i = 0; i < buffer.length; i++) {
        int bpos = i << 1;
        b[bpos] = (byte) ((buffer[i]&0xFF00)>>8);
        b[bpos + 1] = (byte) (buffer[i]&0x00FF);
    }
    return b;
}

public static String paddingString(String src, int length){
    int i = (src.length()/2)%length;    //餘數
    if (i==0) return src;

    int l = 0;
    i = length - i;
    for (l=0;l<i;l++){
        src += "FF";
    }
    return src;
}

//'將byte array中ascii = 0xFF的剔除，取得未被padding的原始字串長度
public static int getPlainTextLength(byte[] bytes){
    if (bytes == null || bytes.length == 0) return 0;
    int i = 0;
    for (i=0;i<bytes.length;i++){
        //Log.d("SecureChat", "i=" + String.valueOf(i) + ", data=" + bytes[i]);
        if (bytes[i] == -1) break;
    }
    return i;
}
/*************************************************************************************/

}
