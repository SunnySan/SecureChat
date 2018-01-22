package com.taisys.sc.securechat.model;

/**
 * Created by sunny.sun on 2018/1/10.
 */

public class ChatMessage {
    private String messageType; //訊息類型，可為 text、audio、file
    private String message;     //訊息內容 (以 3DES key加密過)
    private String senderId;    //Sender 在 firebase 的 ID
    private String receiverId;  //Receiver 在 firebase 的 ID
    private String senderImage; //Sender 頭像 image URL
    private String senderName;  //Sender 人名稱呼
    private long createdAt;     //訊息建立時間，由發送端產生，毫秒
    private String dbKey;       //此訊息在 firebase database 的 unique key
    private boolean decryptedBySender;      //Sender 是否已將訊息解密觀看 (即 "已讀" )
    private boolean decryptedByReceiver;    //Receiver 是否已將訊息解密觀看 (即 "已讀" )
    private boolean decryptedByChatRoom;    //在 chat room 裡面是否已被解密
    private String secretKeyForSender;      //被 Sender public key 加密過的 3DES key
    private String secretKeyForReceiver;    //被 Receiver public key 加密過的 3DES key
    private String localAudioFileUri;   //解密後的 audio 檔案的完整路徑

    public ChatMessage() {
    }

    public ChatMessage(String messageType, String message, String senderId, String receiverId, String secretKeyForSender, String secretKeyForReceiver) {
        this.messageType = messageType;
        this.message = message;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.senderImage = "";
        this.senderName = "";
        this.createdAt = 0;
        this.dbKey = "";
        this.decryptedBySender = false;
        this.decryptedByReceiver = false;
        this.decryptedByChatRoom = false;
        this.secretKeyForSender = secretKeyForSender;
        this.secretKeyForReceiver = secretKeyForReceiver;
        this.localAudioFileUri = "";
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
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

    public boolean getDecryptedBySender() {
        return decryptedBySender;
    }

    public void setDecryptedBySender(boolean decryptedBySender) {this.decryptedBySender = decryptedBySender;}

    public boolean getDecryptedByReceiver() {
        return decryptedByReceiver;
    }

    public void setDecryptedByReceiver(boolean decryptedByReceiver) {this.decryptedByReceiver = decryptedByReceiver;}

    public boolean getDecryptedByChatRoom() {
        return decryptedByChatRoom;
    }

    public void setDecryptedByChatRoom(boolean decryptedByChatRoom) {this.decryptedByChatRoom = decryptedByChatRoom;}


    public String getSecretKeyForSender() {
        return secretKeyForSender;
    }

    public void setSecretKeyForSender(String secretKeyForSender) {this.secretKeyForSender = secretKeyForSender;}

    public String getSecretKeyForReceiver() {
        return secretKeyForReceiver;
    }

    public void setSecretKeyForReceiver(String secretKeyForReceiver) {this.secretKeyForReceiver = secretKeyForReceiver;}

    public String getLocalAudioFileUri() {
        return localAudioFileUri;
    }

    public void setLocalAudioFileUri(String localAudioFileUri) {this.localAudioFileUri = localAudioFileUri;}


}
