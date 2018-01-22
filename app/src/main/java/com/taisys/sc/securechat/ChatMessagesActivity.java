package com.taisys.sc.securechat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.lqr.adapter.LQRAdapterForRecyclerView;
import com.lqr.audio.AudioPlayManager;
import com.lqr.audio.AudioRecordManager;
import com.lqr.audio.IAudioPlayListener;
import com.lqr.audio.IAudioRecordListener;
import com.taisys.oti.Card;
import com.taisys.oti.Card.SCSupported;
import com.taisys.sc.securechat.Application.App;
import com.taisys.sc.securechat.model.ChatMessage;
import com.taisys.sc.securechat.model.User;
import com.taisys.sc.securechat.util.MessagesAdapter;
import com.taisys.sc.securechat.util.Utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import kr.co.namee.permissiongen.PermissionGen;

public class ChatMessagesActivity extends AppCompatActivity {

    private RecyclerView mChatsRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private EditText mMessageEditText;
    private ImageButton mSendImageButton;
    private DatabaseReference mMessagesDBRef;
    private DatabaseReference mUsersRef;
    private StorageReference mAudioStorageRef;
    private List<ChatMessage> mMessagesList = new ArrayList<>();
    private MessagesAdapter adapter = null;

    RelativeLayout mRoot;
    private File mAudioDir;
    private LQRAdapterForRecyclerView<File> mAdapter;
    private ImageButton mRecordAudioIconImageButton;
    private LinearLayout mRecordAudioLayout;
    private ImageButton mRecordAudioImageButton;
    private ImageButton mPlayAudioImageButton;
    private ImageButton mSendAudioImageButton;
    private Uri mAudioUri = null;
    private DatabaseReference mAudioDbRef;

    private String mReceiverId;
    private String mReceiverName;
    private String mReceiverImageUrl;

    private byte[] m3DESSecretKey = null;
    private String mReceiverPublicKey = "";
    private String mSenderPublicKey = "";

    private Card mCard = new Card();
    private ProgressDialog pg = null;
    private Context myContext = null;

    private boolean burnAfterReading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_messages);


        //initialize the views
        mChatsRecyclerView = (RecyclerView)findViewById(R.id.messagesRecyclerView);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendImageButton = (ImageButton)findViewById(R.id.sendMessageImageButtonChatMessage);
        mChatsRecyclerView.setHasFixedSize(true);
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mLayoutManager.setStackFromEnd(true);
        mChatsRecyclerView.setLayoutManager(mLayoutManager);

        mRoot = (RelativeLayout) findViewById(R.id.rootChatMessage);
        mRecordAudioIconImageButton = (ImageButton)findViewById(R.id.recordAudioIconImageButtonChatMessage);
        mRecordAudioLayout = (LinearLayout) findViewById(R.id.recordAudioLayoutChatMessage);
        mRecordAudioLayout.setVisibility(View.GONE);
        mRecordAudioImageButton = (ImageButton)findViewById(R.id.greenMicrophoneImageButtonChatMessage);
        mPlayAudioImageButton = (ImageButton)findViewById(R.id.playAudioImageButtonChatMessage);
        mSendAudioImageButton = (ImageButton)findViewById(R.id.sendAudioImageButtonChatMessage);



        //init Firebase
        mMessagesDBRef = FirebaseDatabase.getInstance().getReference().child("Messages");
        mUsersRef = FirebaseDatabase.getInstance().getReference().child("Users");
        mAudioStorageRef = FirebaseStorage.getInstance().getReference().child("Audios");  //放audio檔案用的

        myContext = this;

        String s = Utility.getMySetting(myContext, "burnAfterReading");
        if (s!=null && s.equals("Y")) burnAfterReading = true;

        //get receiverId from intent
        mReceiverId = getIntent().getStringExtra("USER_ID");
        mReceiverPublicKey = getIntent().getStringExtra("RECEIVER_PUBLIC_KEY");
        mSenderPublicKey = getIntent().getStringExtra("SENDER_PUBLIC_KEY");
        Log.d("SecureChat", "RECEIVER_PUBLIC_KEY= " + mReceiverPublicKey);
        Log.d("SecureChat", "SENDER_PUBLIC_KEY= " + mSenderPublicKey);

        prepareSecretKey(); //動態產生加密資料用的 3DES key，並且用 sender 和 receiver 的 RSA public key 將 3DES key 加密

        /**listen to send message imagebutton click**/
        mSendImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = mMessageEditText.getText().toString();
                String senderId = FirebaseAuth.getInstance().getCurrentUser().getUid();


                if(message.isEmpty()){
                    Toast.makeText(ChatMessagesActivity.this, getString(R.string.msgPleaseEnterYourMessage), Toast.LENGTH_SHORT).show();
                }else {
                    //message is entered, send
                    sendMessageToFirebase(null, "text", message, senderId, mReceiverId, m3DESSecretKey, mSenderPublicKey, mReceiverPublicKey);
                }
            }
        });

        initAudio();

    }

    @Override
    protected void onStart() {
        super.onStart();
        //Log.d("SecureChat", "onStart()");
        /**sets title bar with recepient name**/
        queryRecipientName(mReceiverId);

        /**Query and populate chat messages**/
        //下面這一行改為準備好3DES key以後再做，否則若用戶設定autoDecryptMessage的話會出錯，因為mCard還沒ready
        //querymessagesBetweenThisUserAndClickedUser();

    }

    @Override
    public void onDestroy() {
        //Log.d("SecureChat", "Leave chat room...message count=" + mMessagesList.size());
        doBurnAfterReading();
        if (mCard!=null){
            mCard.CloseSEService();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed(){
        if (mRecordAudioLayout.getVisibility()==View.VISIBLE) {
            mRecordAudioLayout.setVisibility(View.GONE);
        }else{
            this.finish();
        }
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


    private void sendMessageToFirebase(DatabaseReference db, String messageType, String message, String senderId, String receiverId, byte[] byte3DESKey, String encryptedSecretKeyForSender, String encryptedSecretKeyForReceiver){
        //mMessagesList.clear();
        if (byte3DESKey==null) {
            Utility.showMessage(myContext, getString(R.string.msgUnableToFind3DESSecretKey));
            return;
        }

        ChatMessage newMsg = null;

        if (messageType.equals("text")){
            //把訊息內容用 3DES 加密起來
            String encryptedMessage = Utility.encryptString(byte3DESKey, message);
            Log.d("SecureChat", "encrypt message with 3DES key: " + Utility.byte2Hex(byte3DESKey));
            if (encryptedMessage==null || encryptedMessage.length()<1){
                Log.e("SecureChat", "Failed to encrypt message, key= " + byte3DESKey.toString() + ", message= " + message);
                Utility.showMessage(myContext, getString(R.string.msgFailedToEncryptMessage));
                return;
            }
            newMsg = new ChatMessage(messageType, encryptedMessage, senderId, receiverId, encryptedSecretKeyForSender, encryptedSecretKeyForReceiver);
        }

        if (messageType.equals("audio")){
            newMsg = new ChatMessage(messageType, message, senderId, receiverId, encryptedSecretKeyForSender, encryptedSecretKeyForReceiver);
        }
        if (db==null) db = mMessagesDBRef.push();
        newMsg.setCreatedAt(System.currentTimeMillis());
        db.setValue(newMsg).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if(!task.isSuccessful()){
                    //error
                    Toast.makeText(ChatMessagesActivity.this, "Error: " + task.getException().getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }else{
                    //Toast.makeText(ChatMessagesActivity.this, "Message sent successfully!", Toast.LENGTH_SHORT).show();
                    mMessageEditText.setText(null);
                    //hideSoftKeyboard();
                }
            }
        });


    }

    public void hideSoftKeyboard() {
        if(getCurrentFocus()!=null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void querymessagesBetweenThisUserAndClickedUser(){
        /**populate messages**/
        populateMessagesRecyclerView();

        //mMessagesDBRef.addValueEventListener(new ValueEventListener() {
        mMessagesDBRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mMessagesList.clear();
                /* 用下方的 addChildEventListener 取代
                                for(DataSnapshot snap: dataSnapshot.getChildren()){
                                    ChatMessage chatMessage = snap.getValue(ChatMessage.class);
                                    chatMessage.setDbKey(snap.getKey());

                                    if(chatMessage.getSenderId().equals(FirebaseAuth.getInstance().getCurrentUser().getUid()) && chatMessage.getReceiverId().equals(mReceiverId) || chatMessage.getSenderId().equals(mReceiverId) && chatMessage.getReceiverId().equals(FirebaseAuth.getInstance().getCurrentUser().getUid())){
                                        chatMessage.setSenderName(mReceiverName);
                                        chatMessage.setSenderImage(mReceiverImageUrl);
                                        Log.d("SecureChat", "mReceiverImageUrl=" + mReceiverImageUrl);
                                        mMessagesList.add(chatMessage);
                                    }

                                }
                                */


            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(ChatMessagesActivity.this, App.getContext().getResources().getString(R.string.msgFailedToRetrieveDataFromFirebase), Toast.LENGTH_SHORT).show();
            }
        });

        mMessagesDBRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snap, String prevChildKey) {
                ChatMessage chatMessage = snap.getValue(ChatMessage.class);
                chatMessage.setDbKey(snap.getKey());
                Log.d("SecureChat", "message arrive db Key=" + snap.getKey());

                if(chatMessage.getSenderId().equals(FirebaseAuth.getInstance().getCurrentUser().getUid()) && chatMessage.getReceiverId().equals(mReceiverId) || chatMessage.getSenderId().equals(mReceiverId) && chatMessage.getReceiverId().equals(FirebaseAuth.getInstance().getCurrentUser().getUid())){
                    chatMessage.setSenderName(mReceiverName);
                    chatMessage.setSenderImage(mReceiverImageUrl);
                    Log.d("SecureChat", "child event mReceiverImageUrl=" + mReceiverImageUrl);
                    mMessagesList.add(chatMessage);
                    mChatsRecyclerView.scrollToPosition(mMessagesList.size()-1);
                }

            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void populateMessagesRecyclerView(){
        adapter = new MessagesAdapter(mMessagesList, this, mCard);
        mChatsRecyclerView.setAdapter(adapter);

    }

    private void queryRecipientName(final String receiverId){

        mUsersRef.child(receiverId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                User recepient = dataSnapshot.getValue(User.class);
                mReceiverName = recepient.getDisplayName();
                mReceiverImageUrl = recepient.getImage();

                try {
                    getSupportActionBar().setTitle(mReceiverName);
                    getActionBar().setTitle(mReceiverName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    //動態產生加密資料用的 3DES key，並且用 sender 和 receiver 的 RSA public key 將 3DES key 加密
    private void prepareSecretKey(){
        //顯示Progress對話視窗
        showWaiting(getString(R.string.msgPleaseWait), getString(R.string.msgGenerating3DESKey));

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
                            return;
                        }
                    }
                });

    }   //private void prepareSecretKey(){

    private void getCardInfo(){
        String res[] = mCard.GetCardInfo();
        String s = "";
        int i = 0;
        int j = 0;

        if (res != null && res[0].equals(Card.RES_OK)) {
            generate3DESKeyAndEncryptedByRSAPublicKey();
        } else {
            disWaiting();
            Utility.showMessage(myContext, getString(R.string.msgCannotReadCardInfo));
            return;
        }
    }

    private void generate3DESKeyAndEncryptedByRSAPublicKey(){
        byte[] bytes3DESKey = Utility.generate3DESKey();    //動態產生加密資料用的 3DES key
        if (bytes3DESKey==null){
            disWaiting();
            Utility.showMessage(myContext, getString(R.string.msgFailedToGenerate3DESKey));
            return;
        }

        String str3DESKey = Utility.byte2Hex(bytes3DESKey);
        //Log.d("SecureChat", "str3DESKey= " + str3DESKey);
        String resString = "";
        String res[] = null;
        //Log.d("SecureChat", "str3DESKey= " + str3DESKey);
        //將 receiver 的 public key 寫入 0x0202，然後加密 3DES key
        resString = mCard.WriteFile(0x0202, 0, mReceiverPublicKey);
        if (resString != null && resString.equals(Card.RES_OK)) {
            Log.d("SecureChat", "Write mReceiverPublicKey to SIM success！");
        } else {
            m3DESSecretKey = null;
            Log.d("SecureChat", "Write File failed！ error code=" + resString);
            disWaiting();
            Utility.showMessage(myContext, getString(R.string.msgFailedToWriteReceiverPublicKeyToSIM));
            return;
        }

        //用 receiver 的 public key 加密 3DES key
        str3DESKey = Utility.paddingString(str3DESKey, 128);    //進行 padding
        //Log.d("SecureChat", "str3DESKey padding= " + str3DESKey);

        res = mCard.RSAPubKeyCalc(str3DESKey, 0x0202);
        if (res != null && res[0].equals(Card.RES_OK)) {
            Log.d("SecureChat", "SIM card encrypt receiver public key successfully");
            //Log.d("SecureChat", "SIM card encrypted receiver public key= " + res[1]);
            mReceiverPublicKey = res[1];    //將 receiver public key 換成加密過的 3DES key
        }else{
            m3DESSecretKey = null;
            Log.d("SecureChat", "Encrypt 3DES with sender public key failed！ error code=" + res[0]);
            disWaiting();
            Utility.showMessage(myContext, getString(R.string.msgFailedToEncryptSecretKeyForReceiver) + "error: " + res[0]);
            return;
        }


        //用自己的 public key 加密 3DES key
        res = mCard.RSAPubKeyCalc(str3DESKey, 0x0201);
        if (res != null && res[0].equals(Card.RES_OK)) {
            Log.d("SecureChat", "SIM card encrypt sender public key successfully");
            mSenderPublicKey = res[1];    //將 sendver public key 換成加密過的 3DES key
        }else{
            m3DESSecretKey = null;
            Log.d("SecureChat", "Encrypt 3DES with sender public key failed！ error code=" + res[0]);
            disWaiting();
            Utility.showMessage(myContext, getString(R.string.msgFailedToEncryptSecretKeyForSender) + "error: " + res[0]);
            return;
        }

        disWaiting();
        m3DESSecretKey = bytes3DESKey;
        /**Query and populate chat messages**/
        querymessagesBetweenThisUserAndClickedUser();
    }   //private void generate3DESKeyAndEncryptedByRSAPublicKey(){

    //如果用戶有設定 burnAfterReading = true 的話，將已讀的訊息刪除
    private void doBurnAfterReading(){
        //把已經解密的 audio 檔案殺掉 (不論是 sent 或是 received 的 audio 都殺)
        /*
        File[] files = mAudioDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().startsWith("decrypt_");
            }
        });
        */

        File[] files = mAudioDir.listFiles();   //原始檔、加密檔、解密檔全都殺

        if (files!=null && files.length>0){
            for (File file:files){
                if (file.exists()) file.delete();
            }
        }

        if (!burnAfterReading) return;
        if (mMessagesList.isEmpty() || mMessagesList.size()<1) return;
        Log.d("SecureChat", "Do burn after reading");
        showWaiting(getString(R.string.msgPleaseWait), getString(R.string.msgProcessBurnAfterReading));
        int i = 0;
        ChatMessage msg = null;
        String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        for (i=0;i<mMessagesList.size();i++){
            msg = mMessagesList.get(i);
            Log.d("SecureChat", "msg receiver id= " + msg.getReceiverId() + ", decrypted by sender= " + msg.getDecryptedBySender());
            Log.d("SecureChat", "msg receiver id= " + msg.getReceiverId() + ", decrypted by receiver= " + msg.getDecryptedByReceiver());
            if (msg.getReceiverId().equals(myUid) && msg.getDecryptedByReceiver()==true){
                mMessagesDBRef.child(msg.getDbKey()).removeValue();
            }
        }

        disWaiting();
    }
    /************************************************************************************************/
    //以下是和錄音相關的程式，參考 http://www.qingpingshan.com/rjbc/az/248321.html 及 https://github.com/GitLqr/LQRAudioRecord

    //初始化 audio 相關的東西
    private void initAudio() {
        PermissionGen.with(this)
                .addRequestCode(100)
                .permissions(Manifest.permission.RECORD_AUDIO
                        , Manifest.permission.WRITE_EXTERNAL_STORAGE
                        , Manifest.permission.WAKE_LOCK
                        , Manifest.permission.READ_EXTERNAL_STORAGE)
                .request();

        mRecordAudioImageButton.setEnabled(true);
        mPlayAudioImageButton.setEnabled(false);
        mSendAudioImageButton.setEnabled(false);

        mRecordAudioIconImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRecordAudioLayout.getVisibility() == View.VISIBLE) {
                    mRecordAudioLayout.setVisibility(View.GONE);
                } else {
                    mRecordAudioLayout.setVisibility(View.VISIBLE);
                    mRecordAudioImageButton.setEnabled(true);
                    mPlayAudioImageButton.setEnabled(false);
                    mSendAudioImageButton.setEnabled(false);
                    hideSoftKeyboard();
                }

            }
        });

        mRecordAudioImageButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {  //按下的時候
                    mRecordAudioImageButton.setImageResource(R.drawable.microphone_640_red);
                    AudioRecordManager.getInstance(ChatMessagesActivity.this).startRecord();
                    mRecordAudioImageButton.performClick();
                    mPlayAudioImageButton.setEnabled(false);
                    mSendAudioImageButton.setEnabled(false);
                }

                if (event.getAction() == MotionEvent.ACTION_UP) {  //起來的時候
                    AudioRecordManager.getInstance(ChatMessagesActivity.this).stopRecord();
                    AudioRecordManager.getInstance(ChatMessagesActivity.this).destroyRecord();
                    mRecordAudioImageButton.setImageResource(R.drawable.microphone_640_green);
                }

                return false;

            }

        });

        mMessageEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (mRecordAudioLayout.getVisibility() == View.VISIBLE) {
                        mRecordAudioLayout.setVisibility(View.GONE);
                    }
                }
            }
        });

        mMessageEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRecordAudioLayout.getVisibility() == View.VISIBLE) {
                    mRecordAudioLayout.setVisibility(View.GONE);
                }
            }
        });

        mPlayAudioImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playAudio();
            }
        });

        mSendAudioImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                encryptAudioFileAndSendOut();
            }
        });

        AudioRecordManager.getInstance(this).setMaxVoiceDuration(60);
        mAudioDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+ "/SecureChat/audio/");
        if (!mAudioDir.exists()) {
            Log.d("SecureChat", "create mAudioDir");
            mAudioDir.mkdirs();
        }
        Log.d("SecureChat", "mAudioDir" + mAudioDir.getAbsolutePath() + ", " + mAudioDir.exists());

        AudioRecordManager.getInstance(this).setAudioSavePath(mAudioDir.getAbsolutePath());

        AudioRecordManager.getInstance(this).setAudioRecordListener(new IAudioRecordListener() {

            private TextView mTimerTV;
            private TextView mStateTV;
            private ImageView mStateIV;
            private PopupWindow mRecordWindow;

            @Override
            public void initTipView() {
                View view = View.inflate(ChatMessagesActivity.this, R.layout.popup_audio_wi_vo, null);
                mStateIV = (ImageView) view.findViewById(R.id.rc_audio_state_image);
                mStateTV = (TextView) view.findViewById(R.id.rc_audio_state_text);
                mTimerTV = (TextView) view.findViewById(R.id.rc_audio_timer);
                mRecordWindow = new PopupWindow(view, -1, -1);
                mRecordWindow.showAtLocation(mRoot, 17, 0, 0);
                mRecordWindow.setFocusable(true);
                mRecordWindow.setOutsideTouchable(false);
                mRecordWindow.setTouchable(false);
            }

            @Override
            public void setTimeoutTipView(int counter) {
                if (this.mRecordWindow != null) {
                    this.mStateIV.setVisibility(View.GONE);
                    this.mStateTV.setVisibility(View.VISIBLE);
                    this.mStateTV.setText(R.string.voice_rec);
                    this.mStateTV.setBackgroundResource(R.drawable.bg_voice_popup);
                    this.mTimerTV.setText(String.format("%s", new Object[]{Integer.valueOf(counter)}));
                    this.mTimerTV.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void setRecordingTipView() {
                if (this.mRecordWindow != null) {
                    this.mStateIV.setVisibility(View.VISIBLE);
                    this.mStateIV.setImageResource(R.mipmap.ic_volume_1);
                    this.mStateTV.setVisibility(View.VISIBLE);
                    this.mStateTV.setText(R.string.voice_rec);
                    this.mStateTV.setBackgroundResource(R.drawable.bg_voice_popup);
                    this.mTimerTV.setVisibility(View.GONE);
                }
            }

            @Override
            public void setAudioShortTipView() {
                if (this.mRecordWindow != null) {
                    mStateIV.setImageResource(R.mipmap.ic_volume_wraning);
                    mStateTV.setText(R.string.voice_short);
                }
            }

            @Override
            public void setCancelTipView() {
                if (this.mRecordWindow != null) {
                    this.mTimerTV.setVisibility(View.GONE);
                    this.mStateIV.setVisibility(View.VISIBLE);
                    this.mStateIV.setImageResource(R.mipmap.ic_volume_cancel);
                    this.mStateTV.setVisibility(View.VISIBLE);
                    this.mStateTV.setText(R.string.voice_cancel);
                    this.mStateTV.setBackgroundResource(R.drawable.corner_voice_style);
                }
            }

            @Override
            public void destroyTipView() {
                if (this.mRecordWindow != null) {
                    this.mRecordWindow.dismiss();
                    this.mRecordWindow = null;
                    this.mStateIV = null;
                    this.mStateTV = null;
                    this.mTimerTV = null;
                }
            }

            @Override
            public void onStartRecord() {
                //开始录制
            }

            @Override
            public void onFinish(Uri audioPath, int duration) {
                //发送文件
                File file = new File(audioPath.getPath());
                if (file.exists()) {
                    Toast.makeText(getApplicationContext(), getString(R.string.voice_success), Toast.LENGTH_SHORT).show();
                    mRecordAudioImageButton.setEnabled(true);
                    mPlayAudioImageButton.setEnabled(true);
                    mSendAudioImageButton.setEnabled(true);
                    mAudioUri = audioPath;
                }else{
                    Toast.makeText(getApplicationContext(), getString(R.string.voice_failure), Toast.LENGTH_SHORT).show();
                    mRecordAudioImageButton.setEnabled(true);
                    mPlayAudioImageButton.setEnabled(false);
                    mSendAudioImageButton.setEnabled(false);
                }
            }

            @Override
            public void onAudioDBChanged(int db) {
                switch (db / 5) {
                    case 0:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_1);
                        break;
                    case 1:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_2);
                        break;
                    case 2:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_3);
                        break;
                    case 3:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_4);
                        break;
                    case 4:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_5);
                        break;
                    case 5:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_6);
                        break;
                    case 6:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_7);
                        break;
                    default:
                        this.mStateIV.setImageResource(R.mipmap.ic_volume_8);
                }
            }
        });
    }   //private void initAudio(){

    private boolean isCancelled(View view, MotionEvent event) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        if (event.getRawX() < location[0] || event.getRawX() > location[0] + view.getWidth() || event.getRawY() < location[1] - 40) {
            return true;
        }
        return false;
    }

    private void playAudio(){
        if (mAudioUri==null) return;
        AudioPlayManager.getInstance().stopPlay();
        AudioPlayManager.getInstance().startPlay(ChatMessagesActivity.this, mAudioUri, new IAudioPlayListener() {
            @Override
            public void onStart(Uri var1) {
                if (mPlayAudioImageButton != null && mPlayAudioImageButton.getDrawable() instanceof AnimationDrawable) {
                    AnimationDrawable animation = (AnimationDrawable) mPlayAudioImageButton.getDrawable();
                    animation.start();
                }
            }

            @Override
            public void onStop(Uri var1) {
                if (mPlayAudioImageButton != null && mPlayAudioImageButton.getDrawable() instanceof AnimationDrawable) {
                    AnimationDrawable animation = (AnimationDrawable) mPlayAudioImageButton.getDrawable();
                    animation.stop();
                    animation.selectDrawable(0);
                }

            }

            @Override
            public void onComplete(Uri var1) {
                if (mPlayAudioImageButton != null && mPlayAudioImageButton.getDrawable() instanceof AnimationDrawable) {
                    AnimationDrawable animation = (AnimationDrawable) mPlayAudioImageButton.getDrawable();
                    animation.stop();
                    animation.selectDrawable(0);
                }
            }
        });
    }

    //將 audio 檔加密然後送出
    private void encryptAudioFileAndSendOut(){
        mAudioDbRef = null;

        if (mAudioUri==null){
            Utility.showToast(myContext, getString(R.string.msgPleaseRecordAudioFirst));
            return;
        }
        if (m3DESSecretKey==null){
            Utility.showToast(myContext, getString(R.string.msgUnableToFind3DESSecretKey));
            return;
        }

        showWaiting(getString(R.string.msgPleaseWait), getString(R.string.msgEncryptingAndSendingData));
        File originalFile = null;
        File encryptedFile = null;
        try{
            originalFile = new File(mAudioUri.getPath());
            //Log.d("SecureChat", "original file= " + originalFile.getAbsolutePath() + ", key= " + Utility.byte2Hex(m3DESSecretKey));
            encryptedFile = Utility.encryptFile(m3DESSecretKey, originalFile);
            //Log.d("SecureChat", "encryptedFile= " + (encryptedFile==null?"null":encryptedFile.getAbsolutePath()));
            uploadAudioToFirebaseStorage(encryptedFile);
        }catch (Exception e){
            if (encryptedFile!=null && encryptedFile.exists()) encryptedFile.delete();
            Utility.showToast(myContext, getString(R.string.msgFailedToEncryptAudioFile));
            disWaiting();
            return;
        }finally {
            if (originalFile!=null && originalFile.exists()) originalFile.delete();
        }
    }

    private void uploadAudioToFirebaseStorage(File encryptedFile){
        String senderId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        InputStream in = null;
        try {
            in = new FileInputStream(encryptedFile);
            byte[] fileByteArray = new byte[(int) encryptedFile.length()];
            in.read(fileByteArray);
            in.close();

            //先在 Messages database 建立一筆資料，取得這筆資料的 key，然後在 storage 用相同的 key 建資料
            mAudioDbRef = mMessagesDBRef.push();
            String dbKey = mAudioDbRef.getKey();

            // Create file metadata with property to delete
            StorageMetadata metadata = new StorageMetadata.Builder()
                    .setContentType("audio/m4a")
                    .setContentLanguage("en")
                    .build();
            Log.d("SecureChat", "upload audio file message to storage");
            mAudioStorageRef.child(dbKey).putBytes(fileByteArray, metadata).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                    if(!task.isSuccessful()){
                        //error saving audio
                        disWaiting();
                        Utility.showMessage(myContext, getString(R.string.msgFailedToStoreYourFile));
                        return;
                    }else{
                        //success saving audio
                        String storageFileLink = task.getResult().getDownloadUrl().toString();
                        //送 message 出去給 receiver
                        Log.d("SecureChat", "send audio file message to Messages database");
                        sendMessageToFirebase(mAudioDbRef, "audio", storageFileLink, FirebaseAuth.getInstance().getCurrentUser().getUid(), mReceiverId, m3DESSecretKey, mSenderPublicKey, mReceiverPublicKey);
                        disWaiting();
                        return;
                    }
                }
            });

        }catch (Exception e){
            disWaiting();
            Utility.showMessage(myContext, getString(R.string.msgProcessFailed));
        }finally {
            if (encryptedFile!=null && encryptedFile.exists()) encryptedFile.delete();
        }

        return;
    }
}
