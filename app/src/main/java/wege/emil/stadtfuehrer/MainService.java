package wege.emil.stadtfuehrer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

public class MainService extends Service implements LocationListener {
    public static final String ACTION_LOCATION_BROADCAST = MainService.class.getName() + "LocationBroadcast", MESSAGE = "message";
    public static boolean debugMode = false;

    private LocationManager locationManager;
    private TextToSpeech tts;
    private String lastMessage = "";
    MediaPlayer mediaPlayer = new MediaPlayer();

    @Override
    public void onDestroy() {
        locationManager.removeUpdates(this);
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.release();
        if (tts.isSpeaking()) {
            tts.stop();
        }
        tts.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.GERMAN);
                tts.speak("Die App läuft im Hintergrund.", TextToSpeech.QUEUE_FLUSH, null);
            }
        });
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        //sendRequest("https://stadt.emilsleeper.com/api/reset");
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if(locationManager.getAllProviders().contains("gps")) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        2000,
                        10, this);
            } else {
                String message = "Ihr Gerät unterstützt kein GPS, deswegen wir die App nicht funktionieren.";
                changeViewText(message);
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    public JSONObject sendRequest(String targetUrl) {
        HttpsURLConnection connection;
        try {
            URL url = new URL(targetUrl);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setRequestMethod("GET");
            connection.connect();

            InputStream in = new BufferedInputStream(connection.getInputStream());

            @SuppressLint({"NewApi", "LocalSuppress"}) String result = new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining("\n"));
            return new JSONObject(result);
        } catch (IOException | JSONException e) {
            System.out.println(e);
        }
        try {
            return new JSONObject("{\"status\" : \"1\", \"message\" : \"Error.\"}");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void changeViewText(String message) {
        Intent intent = new Intent(ACTION_LOCATION_BROADCAST);
        intent.putExtra(MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (debugMode) {
            changeViewText("Sende Anfrage");
        }
        String content, status, server_message;
        boolean isAudioMessage = false;

        JSONObject server_answer = sendRequest("https://stadt.emilsleeper.com/api/get_nearest_place/"+location.getLatitude()+"/"+location.getLongitude());
        if (debugMode) {
            changeViewText("Antwort erhalten");
        }

        try {
            status = (String) server_answer.get("status");
            server_message = (String) server_answer.get("message");
        } catch (JSONException e) {
            if (debugMode) {
                changeViewText("Der Server gab kein JSON-Objekt zurück, sondern: "+ server_answer);
                return;
            }
            tts.speak("Der Server gab eine ungültige Antwort.", TextToSpeech.QUEUE_ADD, null);
            changeViewText("Der Server gab eine ungültige Antwort.");
            return;
        }
        if (debugMode) {
            changeViewText(server_answer.toString());
            //return;
        }
        switch (status) {
            case "-1":
                content = "Es gab einen schwerwiegenden Fehler: " + server_message;
                break;
            case "0":
                content = "Der Server gab folgenden Error zurück: " + server_message;
                break;
            case "1":
                content = server_message;
                break;
            case "3":
                isAudioMessage = true;
                content = "https://stadt.emilsleeper.com/static/"+server_message;
                break;
            case "2":
                return;
            default:
                content = "Bitte aktualisieren sie die App, falls vorhanden im Play Store, der Server hat einen in dieser Version unbekannten Statuscode zurückgegeben";
                break;
        }
        if (debugMode) {
            changeViewText(content);
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.release();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        if (tts.isSpeaking()) {
            tts.stop();
        }
        if (!lastMessage.equals(content)) {
            if (isAudioMessage) {
                try {
                    mediaPlayer.setDataSource(content);
                    mediaPlayer.setLooping(false);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                changeViewText(content);
                tts.speak(content, TextToSpeech.QUEUE_ADD, null);
            }
        }
        lastMessage = content;
    }

}