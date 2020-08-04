// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.kaldi.demo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.os.Environment;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.security.ConfirmationCallback;
import android.telephony.SmsManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.actions.NoteIntents;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kaldi.Assets;
import org.kaldi.KaldiRecognizer;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.SpeechRecognizer;
import org.kaldi.Vosk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;

public class KaldiActivity extends Activity implements
        RecognitionListener {

    static {
        System.loadLibrary("kaldi_jni");
    }

    String[] words;

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC = 4;

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private static final int PERMISSIONS_REQUEST_CALL_PHONE = 2;
    private static final int PERMISSIONS_REQUEST_SEND_SMS = 3;


    private Model model;
    private SpeechRecognizer recognizer;
    TextView resultView;
    Button dosyaAcma;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // Setup layout
        resultView = findViewById(R.id.result_text);
        setUiState(STATE_START);

        findViewById(R.id.recognize_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recognizeFile();
            }
        });

        findViewById(R.id.recognize_mic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recognizeMicrophone();
            }
        });

        int permissionCheckSMS = ContextCompat.checkSelfPermission(getApplicationContext(),Manifest.permission.SEND_SMS);
        if(permissionCheckSMS != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.SEND_SMS},PERMISSIONS_REQUEST_SEND_SMS);
        }

        int permissionCheckcall = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CALL_PHONE);
        if(permissionCheckcall != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, PERMISSIONS_REQUEST_CALL_PHONE);
        }

        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new SetupTask(this).execute();
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<KaldiActivity> activityReference;

        SetupTask(KaldiActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                Log.d("KaldiDemo", "Sync files in the folder " + assetDir.toString());

                Vosk.SetLogLevel(0);

                activityReference.get().model = new Model(assetDir.toString() + "/model-android");
            } catch (IOException e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                activityReference.get().setErrorState(String.format(activityReference.get().getString(R.string.failed), result));
            } else {
                activityReference.get().setUiState(STATE_READY);
            }
        }
    }

    private static class RecognizeTask extends AsyncTask<Void, Void, String> {
        WeakReference<KaldiActivity> activityReference;
        WeakReference<TextView> resultView;

        RecognizeTask(KaldiActivity activity, TextView resultView) {
            this.activityReference = new WeakReference<>(activity);
            this.resultView = new WeakReference<>(resultView);
        }

        @Override
        protected String doInBackground(Void... params) {
            KaldiRecognizer rec;
            long startTime = System.currentTimeMillis();
            StringBuilder result = new StringBuilder();
            try {
                rec = new KaldiRecognizer(activityReference.get().model, 16000.f, "ama yapamam");

                InputStream ais = activityReference.get().getAssets().open("common_voice_tr_17341278.wav");
                if (ais.skip(44) != 44) {
                    return "";
                }
                byte[] b = new byte[4096];
                int nbytes;
                while ((nbytes = ais.read(b)) >= 0) {
                    if (rec.AcceptWaveform(b, nbytes)) {
                        result.append(rec.Result());
                    } else {
                        result.append(rec.PartialResult());
                    }
                }
                result.append(rec.FinalResult());
            } catch (IOException e) {
                return "";
            }
            return String.format(activityReference.get().getString(R.string.elapsed), result.toString(), (System.currentTimeMillis() - startTime));
        }

        @Override
        protected void onPostExecute(String result) {
            activityReference.get().setUiState(STATE_READY);
            resultView.get().append(result + "\n");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }


    @Override
    public void onResult(String hypothesis) {
        /*resultView.append(hypothesis + "\n");

         */
        try {
            JSONObject object = new JSONObject(hypothesis);
            resultView.append(object.getString("partial"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onPartialResult(String hypothesis) {

        try {
            JSONObject object = new JSONObject(hypothesis);
            String jsonHypo = object.getString("partial");
            resultView.append(jsonHypo + "\n");

            if (jsonHypo.contains("galeri")) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                int requestcode1ForResult = 123;
                startActivityForResult(intent, requestcode1ForResult);
            }

            if (jsonHypo.contains("kamera")) {
                final int REQUEST_TAKE_PHOTO = 1;

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                // Ensure that there's a camera activity to handle the intent
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    // Create the File where the photo should go
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                    } catch (IOException ex) {
                        // Error occurred while creating the File
                    }
                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        Uri photoURI = FileProvider.getUriForFile(this,
                                "com.example.android.fileprovider",
                                photoFile);
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                        startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
                        galleryAddPic();
                    }
                }
            }

            if (jsonHypo.contains("arama")) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                startActivity(intent);
            }

            if (jsonHypo.contains("rehber")) {
                Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                int requestcode3ForResult = 345;
                startActivityForResult(intent, requestcode3ForResult);
            }
            if (jsonHypo.contains("kayıt")) {
                if (jsonHypo.contains("isim")) {
                    Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                    int requestcode3ForResult = 345;
                    startActivityForResult(intent, requestcode3ForResult);
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        recognizer.cancel();
        recognizer = null;
        setUiState(STATE_READY);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                resultView.setText(R.string.preparing);
                resultView.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                break;
            case STATE_READY:
                resultView.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                break;
            case STATE_DONE:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                break;
            case STATE_FILE:
                resultView.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.recognize_file).setEnabled(false);
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                resultView.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(true);
                break;
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
        ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        findViewById(R.id.recognize_file).setEnabled(false);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }

    public void recognizeFile() {
        setUiState(STATE_FILE);
        new RecognizeTask(this, resultView).execute();
    }

    public void recognizeMicrophone() {
        if (recognizer != null) {
            setUiState(STATE_DONE);
            recognizer.cancel();
            recognizer = null;
        } else {
            setUiState(STATE_MIC);
            try {
                recognizer = new SpeechRecognizer(model);
                recognizer.addListener(this);
                recognizer.startListening();
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    //--------------------------------------------------------------------------------------------------------
    String currentPhotoPath;

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(currentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    //-------------------------------------------------------------------------------------------------------
    public void dosyaAc(View view) {



    }
}

    /* --------------------------- ALARM KURMA KODLARI --------------------------------

       -------------- İzinler manifest ve ana kod içerisinde tanımlandı ---------------

        int hour = 8; // Kullanıcıdan alınacak
        int minute = 9; // Kullanıcıdan alınacak

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR,hour);
        intent.putExtra(AlarmClock.EXTRA_MINUTES,minute);

        startActivity(intent);
     */

    /* -------------- UYGULAMA İÇERİSİNDEN DOĞRUDAN ARAMA YAPMA KODLARI ---------------

       -------------- İzinler manifest ve ana kod içerisinde tanımlandı ---------------

        String telNo = "533-111-448-8"; // Kullanıcıdan alınacak

        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + telNo.trim()));
        startActivity(callIntent);
     */

    /* ---------------- UYGULAMA İÇERİSİNDEN DOĞRUDAN SMS ATMA KODLARI ----------------

        -------------- İzinler manifest ve ana kod içerisinde tanımlandı --------------

        String telNo = "533-111-448-8"; // Kullanıcıdan alınacak
        String mesaj = "Merhaba"; //Kullanıcıdan alınacak

        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(telNo,null,mesaj,null,null);
        Toast.makeText(getApplicationContext(),"SMS gönderildi",Toast.LENGTH_LONG).show();

        NOT: Intent kullanarakta kullanıcıdan alınan bilgiler ile sms uygulamasına gidilebilir.
             Ancak burada kullanıcının "gönder" tuşuna basması gerekmektedir.

     */

    /* -------------- NOT DETERİNE NOT KAYDETME KODLARI (SORUN VAR!) ------------------

        final String subject = "subjectt";
        final String text = "textt";

        new AlertDialog.Builder(KaldiActivity.this)
                .setIcon(android.R.drawable.sym_def_app_icon)
                .setTitle("Emin misin?")
                .setMessage("Bu notu kayıt edecek misiniz ?")
                .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(NoteIntents.ACTION_CREATE_NOTE);
                        intent.setType("text/plain");
                        intent.putExtra(NoteIntents.EXTRA_NAME,subject);
                        intent.putExtra(NoteIntents.EXTRA_TEXT,text);

                        if(intent.resolveActivity(getPackageManager()) != null){
                            startActivity(intent);
                        }
                        else{
                            Toast.makeText(getApplicationContext(),"Handle edecek uygulama yok",Toast.LENGTH_LONG).show();
                        }
                    }
                }).setNegativeButton("No",null)
                  .show();

     */

