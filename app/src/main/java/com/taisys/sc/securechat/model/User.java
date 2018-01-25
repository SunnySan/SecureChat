package com.taisys.sc.securechat.model;

/**
 * Created by sunny.sun on 2018/1/10.
 */

public class User {
    private String userId;
    private String displayName;
    private String email;
    private String publicKey;
    private String image;
    private String iccid;


    public User() {
    }

    public User(String userId, String displayName, String email, String publicKey, String iccid) {
        this.userId = userId;
        this.displayName = displayName;
        this.email = email;
        this.publicKey = publicKey;
        this.iccid = iccid;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getIccid() {
        return iccid;
    }

    public void setIccid(String iccid) {
        this.iccid = iccid;
    }

}
