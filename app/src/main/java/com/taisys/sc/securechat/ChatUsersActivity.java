package com.taisys.sc.securechat;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.taisys.sc.securechat.Application.App;
import com.taisys.sc.securechat.model.User;
import com.taisys.sc.securechat.util.LinphoneMiniManager;
import com.taisys.sc.securechat.util.UsersAdapter;
import com.taisys.sc.securechat.util.Utility;

import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;

import java.util.ArrayList;
import java.util.List;

public class ChatUsersActivity extends AppCompatActivity {
    private static final String TAG = "SecureChat";
    //private static final String mVoIPDomain = "taisys.com";
    private static final String mVoIPDomain = "sip.linphone.org";
    private static final String mDefaultPassword = "111111";

    private FirebaseAuth mAuth;
    private DatabaseReference mUsersDBRef;
    private RecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mLayoutManager;
    private UsersAdapter adapter;
    private List<User> mUsersList = new ArrayList<>();
    private String myPublicKey = "";    //目前這個 SIM 卡的 public key

    private LinphoneMiniManager mLinphoneMiniManager;
    private LinphoneCore mLinphoneCore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_users);

        //assign firebase auth
        mAuth = FirebaseAuth.getInstance();
        mUsersDBRef = FirebaseDatabase.getInstance().getReference().child("Users");
        //initialize the recyclerview variables
        mRecyclerView = (RecyclerView)findViewById(R.id.usersRecyclerView);
        mRecyclerView.setHasFixedSize(true);
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mLinphoneMiniManager = App.getLinphoneManager();
        //mLinphoneCore = mLinphoneMiniManager.getLinphoneCore();
        mLinphoneCore = LinphoneMiniManager.getInstance().getLinphoneCore();

    }

    private void populaterecyclerView(){
        adapter = new UsersAdapter(mUsersList, this, myPublicKey);
        mRecyclerView.setAdapter(adapter);
    }

    private void queryUsersAndAddthemToList(){
        //以 displayName 排序顯示
        mUsersDBRef.orderByChild("displayName").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange");
                mUsersList.clear();
                if(dataSnapshot.getChildrenCount() > 0){
                    //Log.d(TAG, "getChildrenCount>0");
                    for(DataSnapshot snap: dataSnapshot.getChildren()){
                        User user = snap.getValue(User.class);
                        //Log.d(TAG, "user: " + user.getDisplayName() + ", public key=" + user.getPublicKey());
                        //if not current user, as we do not want to show ourselves then chat with ourselves lol
                        try {
                            if(!user.getUserId().equals(mAuth.getCurrentUser().getUid())){
                                if (!mUsersList.contains(user)){
                                    //Log.d(TAG, "Add user");
                                    if (user.getPublicKey()!=null && user.getPublicKey().length()>0){
                                        mUsersList.add(user);
                                    }
                                }
                            }else{  //這是目前 SIM 卡用戶，將自己的 public key 記起來，發訊息時要用來加密資料
                                if (user.getPublicKey()!=null && user.getPublicKey().length()>0){
                                    myPublicKey = user.getPublicKey();  //等一下要 pass 給 UsersAdapter.java
                                }
                            }
                        } catch (Exception e) {
                            Toast.makeText(ChatUsersActivity.this, "Error reading contacts information: " + e.toString(), Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    }
                }

                /**populate listview**/
                populaterecyclerView();

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        checkIfUserIsSignIn();

        /**query users and add them to a list**/
        queryUsersAndAddthemToList();
    }

    private void checkIfUserIsSignIn(){
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // User is signed in
            registerSIPAccount();   //跟SIP Server 註冊帳號
        } else {
            // No user is signed in
            /**go to login user first**/
            goToHome();
        }
    }

    private void goToHome(){
        finish();
        //startActivity(new Intent(this, LoginActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.chat_user_menu, menu);
        //invalidateOptionsMenu();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.userProfile:
                goToUpdateUserProfile();
                return true;
            case R.id.changePinCode:
                goToChangePinCode();
                return true;
            case R.id.logout:
                logOutuser();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void goToUpdateUserProfile(){
        //Log.d(TAG, "goToUpdateUserProfile");
        startActivity(new Intent(this, UpdateProfileActivity.class));
    }

    private void goToChangePinCode(){
        //Log.d(TAG, "goToChangePinCode");
        startActivity(new Intent(this, ChangePinCodeActivity.class));
    }

    private void logOutuser(){
        //Log.d(TAG, "goToLogout");
        FirebaseAuth.getInstance().signOut();
        goToHome();
        //now send user back to login screen
        //startActivity(new Intent(this, LoginActivity.class));
    }

    //跟SIP Server 註冊帳號
    private void registerSIPAccount(){
        String myID = mAuth.getCurrentUser().getUid();
        //if (myID.equals("h1E5YDjxhURJcDUO4m1eOJBpbXQ2")) myID = "886986123101"; else myID = "886986123102";
        myID = "+886986123101";
        String identity = "sip:" + myID + "@" + mVoIPDomain;
        try {
            LinphoneProxyConfig proxyConfig = mLinphoneCore.createProxyConfig(identity, mVoIPDomain, null, true);
            proxyConfig.setExpires(300);

            mLinphoneCore.addProxyConfig(proxyConfig);

            LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(
                    myID, mDefaultPassword, null, mVoIPDomain);
            mLinphoneCore.addAuthInfo(authInfo);
            mLinphoneCore.setDefaultProxyConfig(proxyConfig);
            Log.d(TAG, "registered SIP account successfully, account name= " + identity);

        }catch (Exception e){
            Utility.showMessage(this, getString(R.string.msgFailedToRegisterVoiceChatAccount));
            e.printStackTrace();
        }

    }

}
