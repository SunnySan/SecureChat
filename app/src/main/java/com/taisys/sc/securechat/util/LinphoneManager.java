package com.taisys.sc.securechat.util;

/**
 * Created by sunny on 2018/1/23.
 */

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCore.RemoteProvisioningState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

import com.taisys.sc.securechat.R;

public class LinphoneManager implements LinphoneCoreListener {
    private static final String TAG = "SecureChat";
    
    private static LinphoneManager mInstance;
    private Context mContext;
    private LinphoneCore mLinphoneCore;
    private Timer mTimer;

    public LinphoneManager(Context c) {
        mContext = c;
        LinphoneCoreFactory.instance().setDebugMode(true, TAG + "LinphoneManager");

        try {
            String basePath = mContext.getFilesDir().getAbsolutePath();
            copyAssetsFromPackage(basePath);
            mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(this, basePath + "/.linphonerc", basePath + "/linphonerc", null, mContext);
            initLinphoneCoreValues(basePath);

            setUserAgent();
            setFrontCamAsDefault();
            startIterate();
            mInstance = this;
            mLinphoneCore.setMaxCalls(3);
            mLinphoneCore.setNetworkReachable(true); // Let's assume it's true
            mLinphoneCore.enableVideo(false, false);
        } catch (LinphoneCoreException e) {
        } catch (IOException e) {
        }
    }

    public static LinphoneManager getInstance() {
        return mInstance;
    }

    public void destroy() {
        try {
            mTimer.cancel();
            mLinphoneCore.destroy();
        }
        catch (RuntimeException e) {
        }
        finally {
            mLinphoneCore = null;
            mInstance = null;
        }
    }

    private void startIterate() {
        TimerTask lTask = new TimerTask() {
            @Override
            public void run() {
                mLinphoneCore.iterate();
            }
        };

		/*use schedule instead of scheduleAtFixedRate to avoid iterate from being call in burst after cpu wake up*/
        mTimer = new Timer("SecureChat scheduler");
        mTimer.schedule(lTask, 0, 20);
    }

    private void setUserAgent() {
        try {
            String versionName = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
            if (versionName == null) {
                versionName = String.valueOf(mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode);
            }
            mLinphoneCore.setUserAgent("SecureChatAndroid", versionName);
        } catch (NameNotFoundException e) {
        }
    }

    private void setFrontCamAsDefault() {
        int camId = 0;
        AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
        for (AndroidCamera androidCamera : cameras) {
            if (androidCamera.frontFacing)
                camId = androidCamera.id;
        }
        mLinphoneCore.setVideoDevice(camId);
    }

    private void copyAssetsFromPackage(String basePath) throws IOException {
        LinphoneUtils.copyIfNotExist(mContext, R.raw.oldphone_mono, basePath + "/oldphone_mono.wav");
        LinphoneUtils.copyIfNotExist(mContext, R.raw.ringback, basePath + "/ringback.wav");
        LinphoneUtils.copyIfNotExist(mContext, R.raw.toy_mono, basePath + "/toy_mono.wav");
        LinphoneUtils.copyIfNotExist(mContext, R.raw.linphonerc_default, basePath + "/.linphonerc");
        LinphoneUtils.copyFromPackage(mContext, R.raw.linphonerc_factory, new File(basePath + "/linphonerc").getName());
        LinphoneUtils.copyIfNotExist(mContext, R.raw.lpconfig, basePath + "/lpconfig.xsd");
        LinphoneUtils.copyIfNotExist(mContext, R.raw.rootca, basePath + "/rootca.pem");
    }

    private void initLinphoneCoreValues(String basePath) {
        mLinphoneCore.setContext(mContext);
        mLinphoneCore.setRing(null);
        mLinphoneCore.setRootCA(basePath + "/rootca.pem");
        mLinphoneCore.setPlayFile(basePath + "/toy_mono.wav");
        mLinphoneCore.setChatDatabasePath(basePath + "/linphone-history.db");

        int availableCores = Runtime.getRuntime().availableProcessors();
        mLinphoneCore.setCpuCount(availableCores);
    }

    public LinphoneCore getLinphoneCore(){
        return mLinphoneCore;
    }

    @Override
    public void authInfoRequested(LinphoneCore lc, java.lang.String realm, java.lang.String username, java.lang.String domain){
        android.util.Log.d(TAG, "authInfoRequested");

    }

    @Override
    public void globalState(LinphoneCore lc, GlobalState state, String message) {
        Log.d("Global state: " + state + "(" + message + ")");
    }

    @Override
    public void callState(LinphoneCore lc, LinphoneCall call, State cstate,
                          String message) {
        Log.d("Call state: " + cstate + "(" + message + ")");
    }

    @Override
    public void callStatsUpdated(LinphoneCore lc, LinphoneCall call,
                                 LinphoneCallStats stats) {

    }

    @Override
    public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call,
                                      boolean encrypted, String authenticationToken) {

    }

    @Override
    public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg,
                                  RegistrationState cstate, String smessage) {
        Log.d("Registration state: " + cstate + "(" + smessage + ")");
    }

    @Override
    public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf,
                                       String url) {

    }

    @Override
    public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {

    }

    @Override
    public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr,
                                LinphoneChatMessage message) {
        Log.d("Message received from " + cr.getPeerAddress().asString() + " : " + message.getText() + "(" + message.getExternalBodyUrl() + ")");
    }

    @Override
    public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr) {
        Log.d("Composing received from " + cr.getPeerAddress().asString());
    }

    @Override
    public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {

    }

    @Override
    public void ecCalibrationStatus(LinphoneCore lc, EcCalibratorStatus status,
                                    int delay_ms, Object data) {

    }

    @Override
    public void notifyReceived(LinphoneCore lc, LinphoneCall call,
                               LinphoneAddress from, byte[] event) {

    }

    @Override
    public void transferState(LinphoneCore lc, LinphoneCall call,
                              State new_call_state) {

    }

    @Override
    public void infoReceived(LinphoneCore lc, LinphoneCall call,
                             LinphoneInfoMessage info) {

    }

    @Override
    public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev,
                                         SubscriptionState state) {

    }

    @Override
    public void notifyReceived(LinphoneCore lc, LinphoneEvent ev,
                               String eventName, LinphoneContent content) {
        Log.d("Notify received: " + eventName + " -> " + content.getDataAsString());
    }

    @Override
    public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev,
                                    PublishState state) {

    }

    @Override
    public void configuringStatus(LinphoneCore lc,
                                  RemoteProvisioningState state, String message) {
        Log.d("Configuration state: " + state + "(" + message + ")");
    }

    @Override
    public void show(LinphoneCore lc) {

    }

    @Override
    public void displayStatus(LinphoneCore lc, String message) {

    }

    @Override
    public void displayMessage(LinphoneCore lc, String message) {

    }

    @Override
    public void displayWarning(LinphoneCore lc, String message) {

    }

    @Override
    public void uploadStateChanged(LinphoneCore lc, LinphoneCore.LogCollectionUploadState state, java.lang.String info){
        android.util.Log.d(TAG, "uploadStateChanged");

    }

    @Override
    public void friendListCreated(LinphoneCore lc,
                                  LinphoneFriendList list){
        android.util.Log.d(TAG, "friendListCreated");
    }

    @Override
    public void uploadProgressIndication(LinphoneCore lc, int offset, int total){
        android.util.Log.d(TAG, "uploadProgressIndication");

    }

    @Override
    public void fileTransferRecv(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, byte[] buffer, int size){
        android.util.Log.d(TAG, "fileTransferRecv");

    }

    @Override
    public int fileTransferSend(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, java.nio.ByteBuffer buffer, int size){
        android.util.Log.d(TAG, "fileTransferSend");
        return 0;
    }

    @Override
    public void authenticationRequested(LinphoneCore lc, LinphoneAuthInfo authInfo, LinphoneCore.AuthMethod method){
        android.util.Log.d(TAG, "authenticationRequested");

    }

    @Override
    public void networkReachableChanged(LinphoneCore lc, boolean enable){
        android.util.Log.d(TAG, "networkReachableChanged");

    }

    @Override
    public void messageReceivedUnableToDecrypted(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message){
        android.util.Log.d(TAG, "messageReceivedUnableToDecrypted");

    }

    @Override
    public void friendListRemoved(LinphoneCore lc, LinphoneFriendList list){
        android.util.Log.d(TAG, "friendListRemoved");

    }

    @Override
    public void 	fileTransferProgressIndication(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, int progress){
        android.util.Log.d(TAG, "fileTransferProgressIndication");

    }





}
