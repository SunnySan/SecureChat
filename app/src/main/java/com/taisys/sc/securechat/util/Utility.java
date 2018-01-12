package com.taisys.sc.securechat.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.taisys.sc.securechat.R;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/**
 * Created by sunny.sun on 2018/1/12.
 */

public class Utility {
    public static String getMySetting(Context context, String keyName){
        // 建立SharedPreferences物件
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String valueString = sharedPreferences.getString(keyName, "");
        return valueString;
    }

    public static void setMySetting(Context context, String keyName, String value){
        // 建立SharedPreferences物件
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(keyName, value);
        editor.apply();
    }

    public static void showToast(Context context, String msg) {
        if (msg==null || msg.length()==0) return;
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    public static void showMessage(Context context, String msg){
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setTitle(R.string.msgSystemInfo)
                .setMessage(msg)
                .setIcon(R.drawable.ic_launcher)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    //取得 byte array 每個 byte 的 16 進位碼
    public static String byte2Hex(byte[] b) {
        String result = "";
        for (int i=0 ; i<b.length ; i++)
            result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
        return result;
    }

    //將 16 進位碼的字串轉為 byte array
    public static byte[] hex2Byte(String hexString) {
        byte[] bytes = new byte[hexString.length() / 2];
        for (int i=0 ; i<bytes.length ; i++)
            bytes[i] = (byte) Integer.parseInt(hexString.substring(2 * i, 2 * i + 2), 16);
        return bytes;
    }

    //將 byte array 轉成一個個 char 的字串
    public static String bytesToStringUTFCustom(byte[] bytes) {
        char[] buffer = new char[bytes.length >> 1];
        for(int i = 0; i < buffer.length; i++) {
            int bpos = i << 1;
            char c = (char)(((bytes[bpos]&0x00FF)<<8) + (bytes[bpos+1]&0x00FF));
            buffer[i] = c;
        }
        return new String(buffer);
    }

    //將一個個 char 的字串轉成 byte array
    public static byte[] stringToBytesUTFCustom(String str) {
        char[] buffer = str.toCharArray();
        byte[] b = new byte[buffer.length << 1];
        for(int i = 0; i < buffer.length; i++) {
            int bpos = i << 1;
            b[bpos] = (byte) ((buffer[i]&0xFF00)>>8);
            b[bpos + 1] = (byte) (buffer[i]&0x00FF);
        }
        return b;
    }

    public static String paddingString(String src, int length){
        int i = (src.length()/2)%length;    //餘數
        if (i==0) return src;

        int l = 0;
        i = length - i;
        for (l=0;l<i;l++){
            src += "FF";
        }
        return src;
    }

    //'將byte array中ascii = 0xFF的剔除，取得未被padding的原始字串長度
    public static int getPlainTextLength(byte[] bytes){
        if (bytes == null || bytes.length == 0) return 0;
        int i = 0;
        for (i=0;i<bytes.length;i++){
            //Log.d("SecureChat", "i=" + String.valueOf(i) + ", data=" + bytes[i]);
            if (bytes[i] == -1) break;
        }
        return i;
    }

    public static byte[] doRSAEncryption (byte[] data, String sPublicKeyModulus, String sPublicKeyExponent, String algorithm, int mode){
        try {
            BigInteger modulus = new BigInteger(sPublicKeyModulus, 16);
            BigInteger pubExp = new BigInteger(sPublicKeyExponent, 16);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKeySpec pubKeySpec = new RSAPublicKeySpec(modulus, pubExp);
            RSAPublicKey key = (RSAPublicKey) keyFactory.generatePublic(pubKeySpec);

            Cipher cipher = Cipher.getInstance(algorithm);

            // Initiate the cipher.
            if (mode == Cipher.ENCRYPT_MODE)
                cipher.init(Cipher.ENCRYPT_MODE, (RSAPublicKey) key);
            else
                cipher.init(Cipher.DECRYPT_MODE, (RSAPrivateKey) key);

            // Encrypt/Decrypt the data.
            return cipher.doFinal(data);
        } catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException | NoSuchAlgorithmException
                | NoSuchPaddingException | InvalidKeySpecException e) {
            e.printStackTrace();
            Log.d("SecureChat", "RSA failed: " + e.toString());
        }
        return null;
    }

}
