package com.taisys.sc.securechat.model;

/**
 * Created by sunny.sun on 2018/1/10.
 */

public class ChatMessage {
    private String message;
    private String senderId;
    private String receiverId;
    private String senderImage;
    private String senderName;
    private long createdAt;
    private String dbKey;
    private boolean decrypted;

    public ChatMessage() {
    }

    public ChatMessage(String message, String senderId, String receiverId) {
        this.message = message;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.decrypted = false;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {this.senderName = senderName;}

    public String getSenderImage() {
        return senderImage;
    }

    public void setSenderImage(String senderImage) {this.senderImage = senderImage;}

    public long getCreatedAt() {return createdAt;}

    public void setCreatedAt(long createdAt) {this.createdAt = createdAt;}

    public String getDbKey() {
        return dbKey;
    }

    public void setDbKey(String dbKey) {this.dbKey = dbKey;}

    public boolean getDecrypted() {
        return decrypted;
    }

    public void setDecrypted(boolean decrypted) {this.decrypted = decrypted;}

}
