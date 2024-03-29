package de.julianhofmann.h_bank.api;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Pair;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import de.julianhofmann.h_bank.BuildConfig;
import de.julianhofmann.h_bank.util.BalanceCache;
import de.julianhofmann.h_bank.util.PasswordCache;
import de.julianhofmann.h_bank.util.SettingsService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitService {
    private static String ip;
    private static int port;
    private static String protocol;
    private static String serverPassword;

    private static String name = null;
    private static String token = null;

    private static SharedPreferences sharedPreferences;

    private static Retrofit retrofit;
    private static HBankApi hBankApi;

    public static final String SERVER_PASSWORD_KEY_ALIAS = "server_password_key";
    public static final String TOKEN_KEY_ALIAS = "token_key";

    public static void init(SharedPreferences sp) {

        if (sharedPreferences == null) {
            sharedPreferences = sp;
        }

        protocol = sharedPreferences.getString("protocol", null);
        ip = sharedPreferences.getString("ip_address", null);
        port = sharedPreferences.getInt("port", -1);

        String serverPasswordIv = sharedPreferences.getString("server_password_iv", null);
        String encryptedServerPassword = sharedPreferences.getString("server_password", null);

        if (serverPasswordIv != null && encryptedServerPassword != null) {
            serverPassword = decrypt(Base64.decode(serverPasswordIv, Base64.NO_WRAP), Base64.decode(encryptedServerPassword, Base64.NO_WRAP), SERVER_PASSWORD_KEY_ALIAS);
        } else {
            serverPassword = null;
        }

        if (ip != null && port != -1 && serverPassword != null && retrofit == null) {
            OkHttpClient client = new OkHttpClient().newBuilder().readTimeout(10, TimeUnit.SECONDS).connectTimeout(3, TimeUnit.SECONDS).addInterceptor(chain -> {
                Request request = chain.request().newBuilder().addHeader("Password", serverPassword).build();
                return chain.proceed(request);
            }).addInterceptor(chain -> {
                Request request = chain.request().newBuilder().addHeader("Frontend", "android").build();
                return chain.proceed(request);
            }).addInterceptor(chain -> {
                Request request = chain.request().newBuilder().addHeader("Version", Integer.toString(BuildConfig.VERSION_CODE)).build();
                return chain.proceed(request);
            }).build();
            retrofit = new Retrofit.Builder()
                    .baseUrl(getUrl())
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        if (retrofit != null && hBankApi == null) {
            hBankApi = retrofit.create(HBankApi.class);
        }
    }

    public static boolean isLoggedIn() {
        return name != null && token != null;
    }

    public static void login(String loginName, String loginToken) {
        name = loginName;
        token = loginToken;

        SharedPreferences.Editor edit = sharedPreferences.edit();

        edit.putString("name", RetrofitService.getName());

        if (SettingsService.getOfflineLogin()) {
            Pair<byte[], byte[]> encrypted = encrypt(RetrofitService.token, TOKEN_KEY_ALIAS);
            if (encrypted != null) {
                edit.putString("token_iv", Base64.encodeToString(encrypted.first, Base64.NO_WRAP));
                edit.putString("token", Base64.encodeToString(encrypted.second, Base64.NO_WRAP));
            }
        }

        edit.apply();
    }

    public static void clearPassword() {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.remove("token_iv");
        edit.remove("token");
        edit.apply();

        PasswordCache.clearPassword(sharedPreferences);
    }

    public static void reset() {
        name = null;
        token = null;

        String serverPasswordIv = sharedPreferences.getString("server_password_iv", null);
        String encryptedServerPassword = sharedPreferences.getString("server_password", null);

        SharedPreferences.Editor edit = sharedPreferences.edit();

        edit.clear();

        edit.putString("protocol", protocol);
        edit.putString("ip_address", ip);
        edit.putInt("port", port);

        edit.putString("server_password_iv", serverPasswordIv);
        edit.putString("server_password", encryptedServerPassword);

        edit.apply();

        BalanceCache.clear();
        PasswordCache.clearPassword(sharedPreferences);
    }

    public static void logout() {
        name = null;
        token = null;

        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.remove("name");
        edit.remove("token_iv");
        edit.remove("token");

        edit.apply();

        PasswordCache.clearPassword(sharedPreferences);
        BalanceCache.clear();
    }

    public static String getAuthorization() {
        if (token != null) {
            return "Bearer " + token;
        }
        return "";
    }

    public static String getUrl() {
        return protocol + "://"+ip+":"+port+"/";
    }

    public static String getIpAddress() {
        return ip;
    }

    public static String getProtocol() {
        return protocol;
    }

    public static int getPort() {
        return port;
    }

    public static void changeUrl(String protocol, String ip_address, int port, String serverPassword) {
        SharedPreferences.Editor edit = sharedPreferences.edit();

        edit.putString("protocol", protocol);
        edit.putString("ip_address", ip_address);
        edit.putInt("port", port);

        Pair<byte[], byte[]> encryptedServerPassword = encrypt(serverPassword, SERVER_PASSWORD_KEY_ALIAS);

        if (encryptedServerPassword != null) {
            edit.putString("server_password_iv", Base64.encodeToString(encryptedServerPassword.first, Base64.NO_WRAP));
            edit.putString("server_password", Base64.encodeToString(encryptedServerPassword.second, Base64.NO_WRAP));
        }

        edit.apply();

        retrofit = null;
        hBankApi = null;

        init(sharedPreferences);
    }

    private static Pair<byte[], byte[]> encrypt(String text, String keyAlias) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");

            StringBuilder temp = new StringBuilder(text);
            while (temp.toString().getBytes().length % 16 != 0)
                temp.append("\u0020");

            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(keyAlias));

            byte[] ivBytes = cipher.getIV();

            byte[] encryptedBytes = cipher.doFinal(temp.toString().getBytes(StandardCharsets.UTF_8));

            return new Pair<>(ivBytes, encryptedBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String decrypt(byte[] iv, byte[] encryptedText, String keyAlias) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            IvParameterSpec spec = new IvParameterSpec(iv);

            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(keyAlias), spec);

            return new String(cipher.doFinal(encryptedText), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static SecretKey getSecretKey(String alias) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            if (!keyStore.containsAlias(alias)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build();
                keyGenerator.init(spec);
                keyGenerator.generateKey();
            }

            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null);
            return secretKeyEntry.getSecretKey();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Retrofit getRetrofit() {
        return retrofit;
    }

    public static HBankApi getHbankApi() {
        return hBankApi;
    }

    public static String getName() {
        return name;
    }

    public static String getToken() {
        return token;
    }

    public static String getServerPassword() {
        return serverPassword;
    }
}
