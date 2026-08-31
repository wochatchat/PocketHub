package com.pockethub.data.local
import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class TokenCipher @Inject constructor(@ApplicationContext private val context: Context) {
 private val alias="pockethub_credentials_v1"
 private fun key():SecretKey { val k=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}; (k.getEntry(alias,null) as? KeyStore.SecretKeyEntry)?.secretKey?.let{return it}; return KeyGenerator.getInstance("AES","AndroidKeyStore").apply{init(android.security.keystore.KeyGenParameterSpec.Builder(alias,3).setBlockModes("GCM").setEncryptionPaddings("NoPadding").build())}.generateKey() }
 fun encrypt(v:String):String { if(v.isBlank()||v.startsWith("v1:"))return v; val c=Cipher.getInstance("AES/GCM/NoPadding").apply{init(1,key())}; return "v1:"+Base64.encodeToString(c.iv,2)+":"+Base64.encodeToString(c.doFinal(v.toByteArray(StandardCharsets.UTF_8)),2) }
 fun decrypt(v:String):String { if(!v.startsWith("v1:"))return v; return runCatching{val a=v.split(":",limit=3);val c=Cipher.getInstance("AES/GCM/NoPadding").apply{init(2,key(),GCMParameterSpec(128,Base64.decode(a[1],2)))};String(c.doFinal(Base64.decode(a[2],2)),StandardCharsets.UTF_8)}.getOrDefault("") }
}
