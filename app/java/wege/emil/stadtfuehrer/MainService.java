package wege.emil.stadtfuehrer;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.widget.TextView;

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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MainService extends Service implements LocationListener {
    LocationManager locationManager;
    public static final String ACTION_LOCATION_BROADCAST = MainService.class.getName() + "LocationBroadcast", MESSAGE = "message";
    TextToSpeech tts;
    String lastMessage = "";

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.GERMAN);
                tts.speak("Führung aktiviert.", TextToSpeech.QUEUE_FLUSH, null);
            }
        });
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if(locationManager.getAllProviders().contains("gps")) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        2000,
                        50, this);
            } else {
                String message = "Ihr Gerät unterstützt kein GPS, deswegen wir die App nicht funktionieren.";
                changeViewText(message);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public JSONObject sendRequest(String targetUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setRequestMethod("GET");
            connection.connect();

            InputStream in = new BufferedInputStream(connection.getInputStream());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String result = new BufferedReader(new InputStreamReader(in))
                        .lines().collect(Collectors.joining("\n"));
                return new JSONObject(result);
            } else {
                return new JSONObject("{\"status\" : \"-1\", \"message\" : \"Ihr Gerät ist veraltet.\"}");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (SocketTimeoutException e) {
            try {
                return new JSONObject("{\"status\" : \"-1\", \"message\" : \"Der Server hat nicht rechtzeitig geantwortet, falls das Problem weiterhin besteht, sind die Server gerade nicht verfügbar.\"}");
            } catch (JSONException ex) {
                throw new RuntimeException(ex);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void changeViewText(String message) {
        Intent intent = new Intent(ACTION_LOCATION_BROADCAST);
        intent.putExtra(MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        System.out.println("test");
        String message, status, server_message;

        JSONObject server_answer = sendRequest("https://stadt.emilsleeper.com/api/get_nearest_place/"+location.getLatitude()+"/"+location.getLongitude());

        try {
            status = (String) server_answer.get("status");
            server_message = (String) server_answer.get("message");
        } catch (JSONException e) {
            tts.speak("Der Server gab eine ungültige Antwort.", TextToSpeech.QUEUE_ADD, null);
            return;
        }
        switch (status) {
            case "-1":
                message = "Es gab einen schwerwiegenden Fehler: " + server_message;
                break;
            case "0":
                message = "Der Server gab folgenden Error zurück: " + server_message;
                break;
            case "1":
                message = server_message;
                break;
            case "2":
                return;
            default:
                message = "Bitte aktualisieren sie die App im Play Store, der Server hat einen in dieser Version unbekannten Statuscode zurückgegeben";
                break;
        }
        if (!lastMessage.equals(message)) {
            changeViewText(message);
            tts.speak(message, TextToSpeech.QUEUE_ADD, null);
        }
        lastMessage = message;
    }
}