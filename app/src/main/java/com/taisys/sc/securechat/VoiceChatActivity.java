package com.taisys.sc.securechat;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;

import de.hdodenhof.circleimageview.CircleImageView;

public class VoiceChatActivity extends AppCompatActivity{
    private static final String TAG = "SecureChat";
    private static final int ringtoneOutgoing = R.raw.ringtone_04;  //撥出電話時聽到的鈴聲
    private static final int ringtoneIncoming = R.raw.ringtone_03;  //有電話撥入時的震鈴聲

    //private static final String mSIPDomain = "sip.linphone.org";
    //private static final String mSIPDomain = "iptel.org";
    private String mSIPDomain = "";

    private ImageView mUserPhotoImageView;
    private TextView mContactNameTextView;
    private TextView mStatusTextView;
    private CircleImageView mDoVoiceChatBtn;
    private CircleImageView mCancelVoiceChatBtn;

    private String mReceiverId;
    private String mReceiverName;
    private String mReceiverImageUrl;
    private String mReceiverIccid;
    private String mMyId;
    private String mCallerAddress;

    private Context myContext = null;

    private DatabaseReference mUserDBRef;

    private LinphoneMiniManager mLinphoneMiniManager;
    private LinphoneCore mLinphoneCore;
    private LinphoneCall mCall;
    private boolean mIsCalling;

    //顯示畫面的UI Thread上的Handler
    private Handler mUI_Handler = new Handler();
    private Handler mThreadHandler;
    private HandlerThread mThread;

    private MediaPlayer mp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_chat);

        mUserPhotoImageView = (ImageView)findViewById(R.id.userPhotoVoiceChat);
        mContactNameTextView = (TextView) findViewById(R.id.labelVoiceChatContactName);
        mStatusTextView = (TextView) findViewById(R.id.labelVoiceChatStatus);
        mDoVoiceChatBtn = (CircleImageView)findViewById(R.id.doVoiceChatBtn);
        mCancelVoiceChatBtn = (CircleImageView)findViewById(R.id.cancelVoiceChatBtn);

        myContext = this;

        //get receiverId from intent
        mMyId = getIntent().getStringExtra("MY_USER_ID");
        mReceiverId = getIntent().getStringExtra("RECEIVER_USER_ID");
        mReceiverName = getIntent().getStringExtra("RECEIVER_NAME");
        mReceiverImageUrl = getIntent().getStringExtra("RECEIVER_IMAGE_URL");
        mReceiverIccid = getIntent().getStringExtra("RECEIVER_ICCID");
        mCallerAddress = getIntent().getStringExtra("CALLER_ADDRESS");

        Log.d(TAG, "mMyId=" + mMyId);
        Log.d(TAG, "mReceiverId=" + mReceiverId);
        Log.d(TAG, "mReceiverName=" + mReceiverName);
        Log.d(TAG, "mReceiverImageUrl=" + mReceiverImageUrl);
        Log.d(TAG, "mReceiverIccid=" + mReceiverIccid);
        Log.d(TAG, "mCallerAddress=" + mCallerAddress);

        mSIPDomain = Utility.getMySetting(this, "sipDomain");
        mLinphoneMiniManager = App.getLinphoneManager();
        //mLinphoneCore = mLinphoneMiniManager.getLinphoneCore();
        mLinphoneCore = LinphoneMiniManager.getInstance().getLinphoneCore();

        mThread = new HandlerThread("name");
        mThread.start();
        mThreadHandler=new Handler(mThread.getLooper());

        //init firebase
        mUserDBRef = FirebaseDatabase.getInstance().getReference().child("Users");

        mp = null;

        initView();
        initLinphone();
        mUI_Handler.post(updateRegistrationState);

    }

    @Override
    public void onBackPressed() {
        if (mIsCalling){
            Log.i(TAG, "onBackPressed terminate call");
            if (mCall!=null) mLinphoneCore.terminateCall(mCall);
        }
        mIsCalling = false;
        finish();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        try {
            stopRingtone();
            if (mIsCalling){
                Log.i(TAG, "onDestroy terminate call");
                if (mCall!=null) mLinphoneCore.terminateCall(mCall);
            }
            mIsCalling = false;
        }
        catch (RuntimeException e) {
        }
        finally {
            mCall = null;
        }
        super.onDestroy();
    }

    private void initView(){
        if (mCallerAddress!=null && mCallerAddress.length()>0){ //這是 incoming call
            getCallerInfo();
            if (mReceiverIccid==null || mReceiverIccid.length()<1){
                Utility.showMessage(myContext, getString(R.string.msgCannotFindCallerInformation));
                return;
            }
            mStatusTextView.setText(getString(R.string.labrlVoiceCallRinging));
            playRingtone(ringtoneIncoming);
        }else{
            displayUserNameAndPicture();
        }

        mCancelVoiceChatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            /*
                        if (mIsConnected){
                            mIsCalling = false;
                            mIsConnected = false;
                            mLinphoneCore.terminateCall(mCall);
                        }
                        */
            if (mIsCalling && mCall!=null) {
                mLinphoneCore.terminateCall(mCall);
            }else{
                finish();
            }
            }
        });
    }

    private void getCallerInfo(){
        mCall = mLinphoneCore.getCurrentCall();
        if (mCall==null || LinphoneCall.State.IncomingReceived != mCall.getState()){
            Log.e(TAG, "Couldn\'t find incoming call");
            Utility.showMessage(myContext, getString(R.string.msgCoudntFindIncomingCall));
            mDoVoiceChatBtn.setEnabled(false);
            mCall = null;
            mReceiverIccid = "";
            return;
        }

        String sPrefixLength = Utility.getMySetting(myContext, "sipAccountPrefix");
        int iPrefixLength = 0;
        if (sPrefixLength!=null && sPrefixLength.length()>0) iPrefixLength = Integer.parseInt(sPrefixLength);
        String s = mCall.getRemoteAddress().asString().toLowerCase();
        s = s.replaceAll("sip:", "");
        //s = s.substring(1, s.indexOf("@")); //從 1 開始是因為SIP帳號是 8 + iccid，所以要把 8 去掉
        s = s.substring(iPrefixLength, s.indexOf("@")); //從 1 開始是因為SIP帳號是 8 + iccid，所以要把 8 去掉
        mReceiverIccid = s;
        Log.d(TAG, "incoming call, look up caller ICCID: " + s);
        if (mReceiverIccid==null || mReceiverIccid.length()<1) return;

        mUserDBRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.getChildrenCount() > 0){
                    for(DataSnapshot snap: dataSnapshot.getChildren()){
                        User user = snap.getValue(User.class);
                        try {
                            if(user.getIccid()!=null && user.getIccid().equals(mReceiverIccid)){
                                mReceiverId = snap.getKey();
                                mReceiverName = user.getDisplayName();
                                mReceiverImageUrl = user.getImage();
                                Log.d(TAG, "incoming call, look up caller name: " + mReceiverName);
                                displayUserNameAndPicture();
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

    private void displayUserNameAndPicture(){
        //mUserPhotoImageView.setImageURI(Uri.parse(mReceiverImageUrl));
        if (mReceiverImageUrl!=null && mReceiverImageUrl.length()>0){
            Picasso.with(myContext).load(mReceiverImageUrl).placeholder(R.mipmap.ic_launcher).into(mUserPhotoImageView);
        }
        if (mReceiverName!=null && mReceiverName.length()>0) {
            mContactNameTextView.setText(mReceiverName);
        }else{
            mContactNameTextView.setText(getString(R.string.labelUnknown));
        }

    }

    //初始化 Linphone VoIP
    private void initLinphone(){
        mIsCalling = false;
        if (mCallerAddress!=null && mCallerAddress.length()>0 && mCall!=null) { //這是 incoming call
            mIsCalling = true;
            mThreadHandler.post(doVoiceChat);
        }
        mDoVoiceChatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCallerAddress!=null && mCallerAddress.length()>0) { //這是 incoming call
                    try {
                        if (mCall!=null){
                            Log.d(TAG, "OnClick, this is incoming call, user accept call");
                            stopRingtone();
                            mLinphoneCore.acceptCall(mCall);
                            mIsCalling = true;
                            mUI_Handler.post(hideDoVoiceChatButton);
                            //mThreadHandler.post(doVoiceChat);
                        }
                    }catch (Exception e){
                        if (mCall!=null) mLinphoneCore.terminateCall(mCall);
                        mIsCalling = false;
                        Utility.showMessage(myContext, getString(R.string.msgFailedToPickUpTheCall));
                        finish();
                    }
                }else{
                    playRingtone(ringtoneOutgoing);
                    doCallOut();
                }
            }
        });

    }

    private void doCallOut(){
        String receiverId = "";
        receiverId = mReceiverIccid;
        //開始撥打電話
        //mCall = mLinphoneCore.invite("sip:" + receiverId + "@" + mSIPDomain);
        String sipAccountPrefix = Utility.getMySetting(myContext, "sipAccountPrefix");
        if (sipAccountPrefix!=null && sipAccountPrefix.length()>0) receiverId = sipAccountPrefix + receiverId;
        LinphoneAddress la = LinphoneCoreFactory.instance().createLinphoneAddress(receiverId, mSIPDomain, receiverId + "@" + mSIPDomain);
        try {
            mCall = mLinphoneCore.invite(la);
            mIsCalling = true;
            mUI_Handler.post(hideDoVoiceChatButton);
            mThreadHandler.post(doVoiceChat);
        }catch (Exception e){
            stopRingtone();
            mIsCalling = false;
            Utility.showMessage(myContext, getString(R.string.msgFailedToMakeVoiceCall));
            if (mCall!=null) mLinphoneCore.terminateCall(mCall);
        }

    }

    private Runnable doVoiceChat=new Runnable () {
        @Override
        public void run() {
            try {

                long iterateIntervalMs = 50L;

                if (mCall == null) {
                    Log.d(TAG, "Could not place call to " + mReceiverId);
                    mIsCalling = false;
                    return;
                } else {
                    if (mCallerAddress==null || mCallerAddress.length()<1) { //這是 outgoing call
                        Log.d(TAG, "Chat with: " + mReceiverId + ": " + mReceiverName);
                    }
                    mIsCalling = true;

                    while (mIsCalling) {
                        //if (mLinphoneMiniManager.getRegistrationStatus()!=1 || mLinphoneCore==null || mCall==null){
                        if (mLinphoneCore==null || mCall==null){
                            mIsCalling = false;
                            return;
                        }

                        mLinphoneCore.iterate();

                        try {
                            Thread.sleep(iterateIntervalMs);

                            mUI_Handler.post(updateCallDuration);

                            if (mCall.getState().equals(LinphoneCall.State.CallEnd)
                                    || mCall.getState().equals(LinphoneCall.State.CallReleased)) {
                                Log.d(TAG, "mCall.getState()=CallEnd or CallReleased");
                                mIsCalling = false;
                                Utility.showToast(myContext, getString(R.string.labrlVoiceCallEnd));
                            }

                            if (mCall.getState().equals(LinphoneCall.State.StreamsRunning)) {
                                stopRingtone();
                                mIsCalling = true;
                            }

                            if (mCall.getState().equals(LinphoneCall.State.OutgoingRinging)) {
                                mIsCalling = true;
                            }
                        } catch (InterruptedException var8) {
                            Log.d(TAG, "Interrupted! Aborting");
                            stopRingtone();
                            mIsCalling = false;
                        }
                    }
                    if (!LinphoneCall.State.CallEnd.equals(mCall.getState()) && !LinphoneCall.State.CallReleased.equals(mCall.getState())) {
                        Log.d(TAG, "Terminating the call by program");
                        mIsCalling = false;
                        if (mLinphoneCore!=null && mCall!=null) mLinphoneCore.terminateCall(mCall);
                    }
                    finish();
                }
            }catch (Exception e){
                Log.d(TAG, getString(R.string.msgFailedToMakeVoiceCall) + ", error= " + e.toString());
                stopRingtone();
                Utility.showMessage(myContext, getString(R.string.msgFailedToMakeVoiceCall));
                mIsCalling = false;
            }
        }
    };

    private Runnable updateRegistrationState=new Runnable () {
        @Override
        public void run() {
            try {
                int iState = mLinphoneMiniManager.getRegistrationStatus();
                String s = getString(R.string.labelRegistrationRetry);
                if (iState==0) s = getString(R.string.labelRegistrationInProgress);
                if (iState==1) s = getString(R.string.labelRegistrationOk);
                if (iState==5) s = getString(R.string.labelRegistrationFailed);
                if (iState!=1){
                    Utility.showToast(myContext, getString(R.string.msgNotRegisteredToSIPServer));
                    mDoVoiceChatBtn.setEnabled(false);
                }
                getSupportActionBar().setTitle(s);
                getActionBar().setTitle(s);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    };

    private Runnable hideDoVoiceChatButton=new Runnable () {
        @Override
        public void run() {
            mDoVoiceChatBtn.setVisibility(View.GONE);
            mCancelVoiceChatBtn.invalidate();
        }
    };

    private Runnable updateCallDuration=new Runnable () {
        @Override
        public void run() {
            int i = 0;
            if (mIsCalling && mCall!=null) i = mCall.getDuration();
            String s = Utility.secondToHourMinuteSecond(i);
            mStatusTextView.setText(s);
        }
    };

    private void playRingtone(int toneFile){
        stopRingtone();
        mp=MediaPlayer.create(this, toneFile);
        mp.setLooping(true);
        mp.start();
    }

    private void stopRingtone(){
        if (mp!=null){
            mp.release();
            mp = null;
        }
    }

}
