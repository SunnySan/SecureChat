package com.taisys.sc.securechat;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;
import com.taisys.sc.securechat.Application.App;
import com.taisys.sc.securechat.model.User;
import com.taisys.sc.securechat.util.LinphoneMiniManager;
import com.taisys.sc.securechat.util.Utility;

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;

public class VoiceChatIncomingCallActivity extends AppCompatActivity {
    private static final String TAG = "SecureChat";

    private ImageView mUserPhotoImageView;
    private TextView mContactNameTextView;
    private TextView mStatusTextView;
    private Button mDoVoiceChatBtn;
    private Button mCancelVoiceChatBtn;

    private String mCallerId;
    private String mCallerName;
    private String mCallerImageUrl;
    private String mCallerIccid;
    private String mMyId;

    private Context myContext = null;

    private DatabaseReference mUserDBRef;

    private LinphoneMiniManager mLinphoneMiniManager;
    private LinphoneCore mLinphoneCore;
    private LinphoneCall mCall;
    private boolean mIsConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_chat_incoming_call);

        mUserPhotoImageView = (ImageView)findViewById(R.id.userPhotoVoiceChatIncomingCall);
        mContactNameTextView = (TextView) findViewById(R.id.labelVoiceChatIncomingCallContactName);
        mStatusTextView = (TextView) findViewById(R.id.labelVoiceChatIncomingCallStatus);
        mDoVoiceChatBtn = (Button)findViewById(R.id.doVoiceChatIncomingCallBtn);
        mCancelVoiceChatBtn = (Button)findViewById(R.id.cancelVoiceChatIncomingCallBtn);

        myContext = this;

        //get receiverId from intent
        mMyId = getIntent().getStringExtra("MY_USER_ID");

        mLinphoneMiniManager = App.getLinphoneManager();
        //mLinphoneCore = mLinphoneMiniManager.getLinphoneCore();
        mLinphoneCore = LinphoneMiniManager.getInstance().getLinphoneCore();
        mCall = null;
        mIsConnected = false;

        //init firebase
        mUserDBRef = FirebaseDatabase.getInstance().getReference().child("Users");

        getCallerInfo();
        initView();
    }

    @Override
    public void onBackPressed() {
        if (mIsConnected){
            mIsConnected = false;
            mLinphoneCore.terminateCall(mCall);
        }
        finish();
    }

    @Override
    public void onDestroy() {
        try {
            mIsConnected = false;
            //if (mIsCalling)mLinphoneCore.terminateCall(mCall);
        }
        catch (RuntimeException e) {
        }
        finally {
            mCall = null;
        }
        super.onDestroy();
    }

    private void getCallerInfo(){
        mCall = mLinphoneCore.getCurrentCall();
        if (mCall==null || LinphoneCall.State.IncomingReceived != mCall.getState()){
            Log.e(TAG, "Couldn\'t find incoming call");
            Utility.showMessage(myContext, getString(R.string.msgCoudntFindIncomingCall));
            mDoVoiceChatBtn.setEnabled(false);
            mCall = null;
        }

        mUserDBRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.getChildrenCount() > 0){
                    for(DataSnapshot snap: dataSnapshot.getChildren()){
                        User user = snap.getValue(User.class);
                        try {
                            if(!user.getIccid().equals(mCallerIccid)){
                                mCallerId = snap.getKey();
                                mCallerName = user.getDisplayName();
                                mCallerImageUrl = user.getImage();
                            }
                        } catch (Exception e) {
                            Toast.makeText(myContext, "Error reading contacts information: " + e.toString(), Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(myContext, App.getContext().getResources().getString(R.string.msgFailedToRetrieveDataFromFirebase), Toast.LENGTH_SHORT).show();
            }
        });


    }

    private void initView(){
        if (mCall==null) return;
        String s = mCall.getRemoteAddress().asString().toLowerCase();
        s = s.replaceAll("sip:", "");
        s = s.substring(1, s.indexOf("@")); //從 1 開始是因為SIP帳號是 8 + iccid，所以要把 8 去掉
        mCallerIccid = s;
        if (mCallerIccid==null || mCallerIccid.length()<1) return;
        //mUserPhotoImageView.setImageURI(Uri.parse(mReceiverImageUrl));
        if (mCallerImageUrl!=null && mCallerImageUrl.length()>0){
            Picasso.with(myContext).load(mCallerImageUrl).placeholder(R.mipmap.ic_launcher).into(mUserPhotoImageView);
        }
        if (mCallerName!=null && mCallerName.length()>0) {
            mContactNameTextView.setText(mCallerName);
        }else{
            mContactNameTextView.setText(mCallerIccid);
        }

        mCancelVoiceChatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsConnected){
                    mIsConnected = false;
                    mLinphoneCore.terminateCall(mCall);
                }
                finish();
            }
        });
    }





}
