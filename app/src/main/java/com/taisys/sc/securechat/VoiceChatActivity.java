package com.taisys.sc.securechat;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.taisys.sc.securechat.util.Utility;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;

public class VoiceChatActivity extends AppCompatActivity implements LinphoneCoreListener{
    private static final String TAG = "SecureChat";
    private static final String mVoIPDomain = "securechat.sc.taisys.com";
    private static final String mDefaultPassword = "111111";

    private ImageView mUserPhotoImageView;
    private TextView mContactNameTextView;
    private Button mDoVoiceChatBtn;
    private Button mCancelVoiceChatBtn;

    private String mReceiverId;
    private String mReceiverName;
    private String mReceiverImageUrl;
    private String mMyId;
    private boolean mIsCalling;

    private Context myContext = null;

    private LinphoneCore mLinphoneCore;
    private LinphoneCoreListener mLinphoneCoreListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_chat);

        mUserPhotoImageView = (ImageView)findViewById(R.id.userPhotoVoiceChat);
        mContactNameTextView = (TextView) findViewById(R.id.labelVoiceChatContactName);
        mDoVoiceChatBtn = (Button)findViewById(R.id.doVoiceChatBtn);
        mCancelVoiceChatBtn = (Button)findViewById(R.id.cancelVoiceChatBtn);

        myContext = this;

        //get receiverId from intent
        mMyId = getIntent().getStringExtra("MY_USER_ID");
        mReceiverId = getIntent().getStringExtra("RECEIVER_USER_ID");
        mReceiverName = getIntent().getStringExtra("RECEIVER_NAME");
        mReceiverImageUrl = getIntent().getStringExtra("RECEIVER_IMAGE_URL");

        Log.d(TAG, "mMyId=" + mMyId);
        Log.d(TAG, "mReceiverId=" + mReceiverId);
        Log.d(TAG, "mReceiverName=" + mReceiverName);
        Log.d(TAG, "mReceiverImageUrl=" + mReceiverImageUrl);

        initView();
        initLinphone(); //初始化 Linphone VoIP

    }

    private void initView(){
        //mUserPhotoImageView.setImageURI(Uri.parse(mReceiverImageUrl));
        Picasso.with(myContext).load(mReceiverImageUrl).placeholder(R.mipmap.ic_launcher).into(mUserPhotoImageView);
        mContactNameTextView.setText(mReceiverName);

        mCancelVoiceChatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    //初始化 Linphone VoIP
    private void initLinphone(){
        Log.d(TAG, "step -1");
        try {
            Log.d(TAG, "step 0");
            LinphoneCoreFactory.instance().setDebugMode(true, TAG);
            Log.d(TAG, "step 1");
            //mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(mLinphoneCoreListener, myContext);
            mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(this, this);
            Log.d(TAG, "step 2");

            //optional setting based on your needs
            mLinphoneCore.setMaxCalls(3);
            mLinphoneCore.setNetworkReachable(true);
            mLinphoneCore.enableVideo(false, false);

            Log.d(TAG, "step 3");
            mDoVoiceChatBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doVoiceChat();
                }
            });

            mIsCalling = true;
        }catch (Exception e){
            Log.d(TAG, "step 4");
            Utility.showMessage(myContext, getString(R.string.msgFailedToInitializeVoiceChatModule) + ", error: " + e.toString());
            mDoVoiceChatBtn.setEnabled(false);
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (mLinphoneCore!=null) mLinphoneCore.destroy();
        } catch (RuntimeException e) {
            Log.d(TAG, "VoiceChatActivity onDestroy exception");
            e.printStackTrace();
        } finally {
            mLinphoneCore = null;
        }
        super.onDestroy();
    }

    @Override
    public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf, java.lang.String url){
        Log.d(TAG, "newSubscriptionRequest");
    }

    @Override
    public void friendListCreated(LinphoneCore lc,
                                  LinphoneFriendList list){
        Log.d(TAG, "friendListCreated");
    }

    @Override
    public void uploadProgressIndication(LinphoneCore lc, int offset, int total){
        Log.d(TAG, "uploadProgressIndication");

    }

    @Override
    public void friendListRemoved(LinphoneCore lc, LinphoneFriendList list){
        Log.d(TAG, "friendListRemoved");

    }

    @Override
    public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State cstate, java.lang.String msg){
        Log.d(TAG, "callState");
        //接聽電話
        if(cstate.equals(LinphoneCall.State.IncomingReceived)) {
            // YOU HAVE AN INCOMING CALL
        }
    }

    @Override
    public void infoReceived(LinphoneCore lc, LinphoneCall call, LinphoneInfoMessage info){
        Log.d(TAG, "infoReceived");

    }

    @Override
    public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message){
        Log.d(TAG, "messageReceived");

    }

    @Override
    public void networkReachableChanged(LinphoneCore lc, boolean enable){
        Log.d(TAG, "networkReachableChanged");

    }

    @Override
    public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call, boolean encrypted, java.lang.String authenticationToken){
        Log.d(TAG, "callEncryptionChanged");

    }

    @Override
    public void transferState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State new_call_state){
        Log.d(TAG, "transferState");

    }

    @Override
    public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf){
        Log.d(TAG, "notifyPresenceReceived");

    }

    @Override
    public void callStatsUpdated(LinphoneCore lc, LinphoneCall call, LinphoneCallStats stats){
        Log.d(TAG, "callStatsUpdated");

    }

    @Override
    public void fileTransferRecv(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, byte[] buffer, int size){
        Log.d(TAG, "fileTransferRecv");

    }

    @Override
    public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev, SubscriptionState state){
        Log.d(TAG, "subscriptionStateChanged");

    }

    @Override
    public void displayMessage(LinphoneCore lc, java.lang.String message){
        Log.d(TAG, "displayMessage");

    }

    @Override
    public void notifyReceived(LinphoneCore lc, LinphoneCall call, LinphoneAddress from, byte[] event){
        Log.d(TAG, "notifyReceived");

    }

    @Override
    public void notifyReceived(LinphoneCore lc, LinphoneEvent ev, java.lang.String eventName, LinphoneContent content){
        Log.d(TAG, "notifyReceived");

    }

    @Override
    public void uploadStateChanged(LinphoneCore lc, LinphoneCore.LogCollectionUploadState state, java.lang.String info){
        Log.d(TAG, "uploadStateChanged");

    }

    @Override
    public void messageReceivedUnableToDecrypted(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message){
        Log.d(TAG, "messageReceivedUnableToDecrypted");

    }

    @Override
    public void displayWarning(LinphoneCore lc, java.lang.String message){
        Log.d(TAG, "displayWarning");

    }

    @Override
    public int fileTransferSend(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, java.nio.ByteBuffer buffer, int size){
        Log.d(TAG, "fileTransferSend");
        return 0;
    }

    @Override
    public void 	globalState(LinphoneCore lc, LinphoneCore.GlobalState state, java.lang.String message){
        Log.d(TAG, "globalState");

    }

    @Override
    public void configuringStatus(LinphoneCore lc, LinphoneCore.RemoteProvisioningState state, java.lang.String message){
        Log.d(TAG, "configuringStatus");

    }

    @Override
    public void authInfoRequested(LinphoneCore lc, java.lang.String realm, java.lang.String username, java.lang.String domain){
        Log.d(TAG, "authInfoRequested");

    }

    @Override
    public void ecCalibrationStatus(LinphoneCore lc, LinphoneCore.EcCalibratorStatus status, int delay_ms, java.lang.Object data){
        Log.d(TAG, "ecCalibrationStatus");

    }

    @Override
    public void show(LinphoneCore lc){
        Log.d(TAG, "show");

    }

    @Override
    public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf){
        Log.d(TAG, "dtmfReceived");

    }

    @Override
    public void authenticationRequested(LinphoneCore lc, LinphoneAuthInfo authInfo, LinphoneCore.AuthMethod method){
        Log.d(TAG, "authenticationRequested");

    }

    @Override
    public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState cstate, java.lang.String smessage){
        Log.d(TAG, "registrationState");

    }

    @Override
    public void 	fileTransferProgressIndication(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, int progress){
        Log.d(TAG, "fileTransferProgressIndication");

    }

    @Override
    public void displayStatus(LinphoneCore lc, java.lang.String message){
        Log.d(TAG, "displayStatus");

    }

    @Override
    public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr){
        Log.d(TAG, "isComposingReceived");

    }

    @Override
    public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev, PublishState state){
        Log.d(TAG, "publishStateChanged");

    }









    private void doVoiceChat(){
        //跟SIP Server 註冊帳號
        String identity = "sip:" + mMyId + "@" + mVoIPDomain;
        try {
            LinphoneProxyConfig proxyConfig = mLinphoneCore.createProxyConfig(identity, mVoIPDomain, null, true);
            proxyConfig.setExpires(300);

            mLinphoneCore.addProxyConfig(proxyConfig);

            LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(
                    mMyId, mDefaultPassword, null, mVoIPDomain);
            mLinphoneCore.addAuthInfo(authInfo);
            mLinphoneCore.setDefaultProxyConfig(proxyConfig);

            //開始撥打電話
            LinphoneCall call = mLinphoneCore.invite(mReceiverId);

            boolean isConnected = false;
            long iterateIntervalMs = 50L;

            if (call == null) {
                Log.d(TAG, "Could not place call to");
            } else {
                Log.d(TAG, "Call to: " + mReceiverId);

                while (mIsCalling) {
                    mLinphoneCore.iterate();

                    try {
                        Thread.sleep(iterateIntervalMs);

                        if (call.getState().equals(LinphoneCall.State.CallEnd)
                                || call.getState().equals(LinphoneCall.State.CallReleased)) {
                            mIsCalling = false;
                        }

                        if (call.getState().equals(LinphoneCall.State.StreamsRunning)) {
                            isConnected = true;
                            // do your stuff
                        }

                        if (call.getState().equals(LinphoneCall.State.OutgoingRinging)) {
                            // do your stuff
                        }


                    } catch (InterruptedException var8) {
                        Log.d(TAG, "Interrupted! Aborting");
                    }
                }
                if (!LinphoneCall.State.CallEnd.equals(call.getState())) {
                    Log.d(TAG, "Terminating the call");
                    mLinphoneCore.terminateCall(call);
                }
            }
        }catch (Exception e){
            Utility.showMessage(myContext, getString(R.string.msgFailedToMakeVoiceCall));
        }
    }

}
