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
import com.taisys.sc.securechat.Application.App;
import com.taisys.sc.securechat.util.LinphoneManager;
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

public class VoiceChatActivity extends AppCompatActivity{
    private static final String TAG = "SecureChat";
    private static final String mVoIPDomain = "taisys.com";
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

    private LinphoneManager mLinphoneManager;
    private LinphoneCore mLinphoneCore;

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

        mLinphoneManager = App.getLinphoneManager();
        mLinphoneCore = mLinphoneManager.getLinphoneCore();

        initView();
        initLinphone();
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
        mDoVoiceChatBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doVoiceChat();
            }
        });

        mIsCalling = true;
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
