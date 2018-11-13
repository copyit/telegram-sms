package com.qwe7002.telegram_sms;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class main_activity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Context context = getApplicationContext();
        final EditText chat_id = findViewById(R.id.chat_id);
        final EditText bot_token = findViewById(R.id.bot_token);
        final EditText trusted_phone_number = findViewById(R.id.trusted_phone_number);
        final EditText webhook_listening_port = findViewById(R.id.webhook_listening_port);
        final Switch webhook = findViewById(R.id.webhook);
        final Switch fallback_sms = findViewById(R.id.fallback_sms);

        final SharedPreferences sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);
        String bot_token_save = sharedPreferences.getString("bot_token", "");
        String chat_id_save = sharedPreferences.getString("chat_id", "");
        boolean initialized = sharedPreferences.getBoolean("initialized", false);
        if (!initialized && !bot_token_save.isEmpty() && !chat_id_save.isEmpty()) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("initialized", true);
            editor.apply();
            initialized = true;
        }
        if (initialized) {
            public_func.start_service(context, sharedPreferences);
        }

        Button save_button = findViewById(R.id.save);
        Button get_id = findViewById(R.id.get_id);
        Button logcat = findViewById(R.id.logcat_button);

        bot_token.setText(bot_token_save);
        chat_id.setText(chat_id_save);

        trusted_phone_number.setText(sharedPreferences.getString("trusted_phone_number", ""));
        fallback_sms.setChecked(sharedPreferences.getBoolean("fallback_sms", false));
        webhook_listening_port.setText(String.valueOf(sharedPreferences.getInt("webhook_listening_port", 5000)));
        webhook.setChecked(sharedPreferences.getBoolean("webhook", false));
        logcat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent logcat_intent = new Intent(main_activity.this, logcat_activity.class);
                startActivity(logcat_intent);
            }
        });
        get_id.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (bot_token.getText().toString().isEmpty()) {
                    Snackbar.make(v, R.string.token_not_configure, Snackbar.LENGTH_LONG).show();
                    return;
                }
                final ProgressDialog mpDialog = new ProgressDialog(main_activity.this);
                mpDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mpDialog.setTitle(getString(R.string.connect_wait_title));
                mpDialog.setMessage(getString(R.string.connect_wait_message));
                mpDialog.setIndeterminate(false);
                mpDialog.setCancelable(false);
                mpDialog.show();
                String request_uri = "https://api.telegram.org/bot" + bot_token.getText().toString().trim() + "/getUpdates";
                OkHttpClient okHttpClient = new OkHttpClient();
                Request request = new Request.Builder().url(request_uri).build();
                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        mpDialog.cancel();
                        String error_message = "Get ID Network Error：" + e.getMessage();
                        Snackbar.make(v, error_message, Snackbar.LENGTH_LONG).show();
                        public_func.write_log(context, error_message);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Looper.prepare();
                        mpDialog.cancel();
                        if (response.code() != 200) {
                            assert response.body() != null;
                            String result = response.body().string();
                            JsonObject result_obj = new JsonParser().parse(result).getAsJsonObject();
                            String error_message = "Get ID API Error:" + result_obj.get("description").getAsString();
                            public_func.write_log(context, error_message);
                            Snackbar.make(v, error_message, Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        assert response.body() != null;
                        String result = response.body().string();
                        JsonObject result_obj = new JsonParser().parse(result).getAsJsonObject();
                        JsonArray chat_list = result_obj.getAsJsonArray("result");
                        if (chat_list.size() == 0) {
                            Snackbar.make(v, R.string.no_recent, Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        final ArrayList<String> chat_name_list = new ArrayList<>();
                        final ArrayList<String> chat_id_list = new ArrayList<>();
                        for (JsonElement item : chat_list) {
                            JsonObject item_obj = item.getAsJsonObject();
                            if (item_obj.has("message")) {
                                JsonObject message_obj = item_obj.get("message").getAsJsonObject();
                                JsonObject chat_obj = message_obj.get("chat").getAsJsonObject();
                                if (!chat_id_list.contains(chat_obj.get("id").getAsString())) {
                                    chat_name_list.add(chat_obj.get("username").getAsString() + "(Chat)");
                                    chat_id_list.add(chat_obj.get("id").getAsString());
                                }
                            }
                            if (item_obj.has("channel_post")) {
                                JsonObject message_obj = item_obj.get("channel_post").getAsJsonObject();
                                JsonObject chat_obj = message_obj.get("chat").getAsJsonObject();
                                if (!chat_id_list.contains(chat_obj.get("id").getAsString())) {
                                    chat_name_list.add(chat_obj.get("title").getAsString() + "(Channel)");
                                    chat_id_list.add(chat_obj.get("id").getAsString());
                                }
                            }
                        }
                        main_activity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(v.getContext()).setTitle(R.string.select_chat).setItems(chat_name_list.toArray(new String[0]), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        chat_id.setText(chat_id_list.get(i));

                                    }
                                }).setPositiveButton("Cancel", null).show();
                            }
                        });
                        Looper.loop();
                    }
                });
            }
        });

        save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (bot_token.getText().toString().isEmpty() || chat_id.getText().toString().isEmpty()) {
                    Snackbar.make(v, R.string.chatid_or_token_not_config, Snackbar.LENGTH_LONG).show();
                    return;
                }
                ActivityCompat.requestPermissions(main_activity.this, new String[]{Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS}, 1);
                final ProgressDialog mpDialog = new ProgressDialog(main_activity.this);
                mpDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mpDialog.setTitle(getString(R.string.connect_wait_title));
                mpDialog.setMessage(getString(R.string.connect_wait_message));
                mpDialog.setIndeterminate(false);
                mpDialog.setCancelable(false);
                mpDialog.show();
                String request_uri = "https://api.telegram.org/bot" + bot_token.getText().toString().trim() + "/sendMessage";
                request_json request_body = new request_json();
                request_body.chat_id = chat_id.getText().toString().trim();
                request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.success_connect);
                Gson gson = new Gson();
                String request_body_raw = gson.toJson(request_body);
                RequestBody body = RequestBody.create(public_func.JSON, request_body_raw);
                OkHttpClient okHttpClient = public_func.get_okhttp_obj();
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Looper.prepare();
                        mpDialog.cancel();
                        String error_message = "Send Message Network Error:" + e.getMessage();
                        public_func.write_log(context, error_message);
                        Snackbar.make(v, error_message, Snackbar.LENGTH_LONG)
                                .show();
                        Looper.loop();
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        Looper.prepare();
                        mpDialog.cancel();
                        if (response.code() != 200) {
                            assert response.body() != null;
                            String result = response.body().string();
                            JsonObject result_obj = new JsonParser().parse(result).getAsJsonObject();
                            String error_message = "Send Message API Error:" + result_obj.get("description");
                            public_func.write_log(context, error_message);
                            Snackbar.make(v, error_message, Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("bot_token", bot_token.getText().toString().trim());
                        editor.putString("chat_id", chat_id.getText().toString().trim());
                        editor.putString("trusted_phone_number", trusted_phone_number.getText().toString().trim());
                        editor.putInt("webhook_listening_port", Integer.parseInt(webhook_listening_port.getText().toString()));
                        editor.putBoolean("fallback_sms", fallback_sms.isChecked());
                        editor.putBoolean("webhook", webhook.isChecked());
                        editor.putBoolean("initialized", true);
                        editor.apply();
                        Snackbar.make(v, "Success", Snackbar.LENGTH_LONG)
                                .show();
                        public_func.restart_service(context, sharedPreferences);
                        Looper.loop();
                    }
                });
            }
        });
    }

}

