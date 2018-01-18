package com.taisys.sc.securechat;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.taisys.oti.Card;
import com.taisys.oti.Card.SCSupported;
import com.taisys.sc.securechat.Application.App;
import com.taisys.sc.securechat.model.ChatMessage;
import com.taisys.sc.securechat.model.User;
import com.taisys.sc.securechat.util.MessagesAdapter;
import com.taisys.sc.securechat.util.Utility;

import java.util.ArrayList;
import java.util.List;

public class ChatMessagesActivity extends AppCompatActivity {

    private RecyclerView mChatsRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private EditText mMessageEditText;
    private ImageButton mSendImageButton;
    private DatabaseReference mMessagesDBRef;
    private DatabaseReference mUsersRef;
    private List<ChatMessage> mMessagesList = new ArrayList<>();
    private MessagesAdapter adapter = null;

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
        mSendImageButton = (ImageButton)findViewById(R.id.sendMessageImagebutton);
        mChatsRecyclerView.setHasFixedSize(true);
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mLayoutManager.setStackFromEnd(true);
        mChatsRecyclerView.setLayoutManager(mLayoutManager);

        //init Firebase
        mMessagesDBRef = FirebaseDatabase.getInstance().getReference().child("Messages");
        mUsersRef = FirebaseDatabase.getInstance().getReference().child("Users");

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
                    Toast.makeText(ChatMessagesActivity.this, "You must enter a message", Toast.LENGTH_SHORT).show();
                }else {
                    //message is entered, send
                    sendMessageToFirebase(message, senderId, mReceiverId, m3DESSecretKey, mSenderPublicKey, mReceiverPublicKey);
                }
            }
        });




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


    private void sendMessageToFirebase(String message, String senderId, String receiverId, byte[] byte3DESKey, String encryptedSecretKeyForSender, String encryptedSecretKeyForReceiver){
        //mMessagesList.clear();
        if (byte3DESKey==null) {
            Utility.showMessage(myContext, getString(R.string.msgUnableToFind3DESSecretKey));
            return;
        }
        //把訊息內容用 3DES 加密起來
        String encryptedMessage = Utility.encryptString(byte3DESKey, message);
        Log.d("SecureChat", "encrypt message with 3DES key: " + Utility.byte2Hex(byte3DESKey));
        if (encryptedMessage==null || encryptedMessage.length()<1){
            Log.e("SecureChat", "Failed to encrypt message, key= " + byte3DESKey.toString() + ", message= " + message);
            Utility.showMessage(myContext, getString(R.string.msgFailedToEncryptMessage));
            return;
        }
        ChatMessage newMsg = new ChatMessage(encryptedMessage, senderId, receiverId, encryptedSecretKeyForSender, encryptedSecretKeyForReceiver);
        newMsg.setCreatedAt(System.currentTimeMillis());
        mMessagesDBRef.push().setValue(newMsg).addOnCompleteListener(new OnCompleteListener<Void>() {
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

                /**populate messages**/
                populateMessagesRecyclerView();

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
}
