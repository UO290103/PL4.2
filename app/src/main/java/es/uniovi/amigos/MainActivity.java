package es.uniovi.amigos;
import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity{
    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView map = null;  // Este atributo guarda una referencia al objeto MapView
    // a través del cual podremos manipular el mapa que se muestre
    private String AMIGOS_URL = "https://man-assuring-possibly.ngrok-free.app/api/amigo";
    //URL estatica dada por ngrok con la que accedemos al servicio API rest de los amigos
    private Amigo mUser = new Amigo(0,"user",0.0,0.0);
    //Información del usuario guardada
    private boolean isUserCreated = false;
    //booleano que nos indica si el usuario ya fue creado o no

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Leer la configuración de la aplicación
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        // Crear el mapa desde el layout y asignarle la fuente de la que descargará las imágenes del mapa
        setContentView(R.layout.activity_main);
        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);

        // Solicitar al usuario los permisos "peligrosos". El usuario debe autorizarlos
        // Cuando los autorice, Android llamará a la función onRequestPermissionsResult
        // que implementamos más adelante
        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION
        });

        //Centramos el mapa en Europa
        centerMapOnEurope();
        //Tarea asincrona para obtener la lista de amigos
        new ShowAmigosTask().execute(AMIGOS_URL);
        //Obtenemos el nombre del usuario
        askUserName();
        //Iniciamos timer para el polling
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new UpdateAmigoPosition(), 0, 5000);
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // Esta función será invocada cuando el usuario conceda los permisos
        // De momento hay que dejarla como está
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (int i = 0; i < grantResults.length; i++) {
            permissionsToRequest.add(permissions[i]);
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        // Itera por la lista de permisos y los va solicitando de uno en uno
        // a menos que estén ya concedidos (de ejecuciones previas)
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                permissionsToRequest.add(permission);
            }
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }
    //Metodo que centra el mapa en unas coordenadas especificas
    void centerMapOnEurope() {
        // Esta función mueve el centro del mapa a Paris y ajusta el zoom
        // para que se vea Europa
        IMapController mapController = map.getController();
        mapController.setZoom(5.5);
        GeoPoint startPoint = new GeoPoint(48.8583, 2.2944);
        mapController.setCenter(startPoint);
    }
    //Metodo que añade un marcador al mapa en la posicion indicada
    private void addMarker(double latitud, double longitud, String name) {
        GeoPoint coords = new GeoPoint(latitud, longitud);
        Marker startMarker = new Marker(map);
        startMarker.setPosition(coords);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        startMarker.setTitle(name);
        map.getOverlays().add(startMarker);
    }
    //Metodo que teniendo una lista de Objetos amigo añade un marcador al mapa con la ubicacion
    //cada uno
    private void addListToMap(List<Amigo> amigoList){
        //Limpiamos el mapa de los marcadores anteriores
        map.getOverlays().clear();
        //Recorremos la lista y añadimos el marcador de cada amigo
        for(int i=0;i<amigoList.size();i++){
            var amigo = amigoList.get(i);
            addMarker(amigo.lati,amigo.longi,amigo.name);
        }
        //Simulamos un scroll para que actualice el mapa
        map.getController().scrollBy(0,0);
    }

    public void askUserName() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Settings");
        alert.setMessage("User name:");

        // Crear un EditText para obtener el nombre
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                mUser.name = input.getText().toString();
                Toast.makeText(MainActivity.this,"Tu nombre de usuario: "+mUser.name,Toast.LENGTH_SHORT).show();
                //Creamos el Listener del GPS cuando ya tenemos nombre
                SetupLocation();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Toast.makeText(MainActivity.this,"Se necesita nombre de usuario",Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        alert.show();
    }
    //Metodo que crea un Listener de la ubicación y notifica cuando ha cambiado al menos 10 metros
    void SetupLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
        {
            // Verificar por si acaso si tenemos el permiso, y si no
            // no hacemos nada
            return;
        }
        // Se debe adquirir una referencia al Location Manager del sistema
        LocationManager locationManager =
                (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        // Se obtiene el mejor provider de posición
        Criteria criteria = new Criteria();
        String  provider = locationManager.getBestProvider(criteria, false);

        // Se crea un listener de la clase que se va a definir luego
        MyLocationListener locationListener = new MyLocationListener();

        // Se registra el listener con el Location Manager para recibir actualizaciones
        // En este caso pedimos que nos notifique la nueva localización
        // si el teléfono se ha movido más de 10 metros
        locationManager.requestLocationUpdates(provider, 0, 10, locationListener);

        // Comprobar si se puede obtener la posición ahora mismo
        Location location = locationManager.getLastKnownLocation(provider);
        if (location != null) {

        } else {
            // Actualmente no se puede obtener la posición
        }
    }
    // Se define un Listener para escuchar por cambios en la posición
    class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            //Obtenemos las nuevas latidud y longitud
            double lati = location.getLatitude();
            double longi = location.getLongitude();
            // Actualiza la posición del usuario
            mUser.lati = lati;
            mUser.longi = longi;
            // Envía la nueva posición al backend
            new SendLocationTask().execute(AMIGOS_URL);
        }

        // El resto de métodos que debemos implementar los podemos dejar vacíos
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
        // Se llama cuando se activa el provider
        @Override
        public void onProviderEnabled(String provider) {}
        // Se llama cuando se desactiva el provider
        @Override
        public void onProviderDisabled(String provider) {}
    }

    //Metodo para parsear el InputStream de la respuesta a String
    private String readStream(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    class ShowAmigosTask extends AsyncTask<String, Void, List<Amigo>> {
        //doInBackground de ShowAmigosTask, manda una petición GET a la API y recibe una lista de Amigos
        @Override
        protected List<Amigo> doInBackground(String... urls) {
            try {
                String json = readStream(openUrl(urls[0]));
                return parseDataFromNetwork(json);
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                return null;
            }
        }
        //onPostExecute de ShowAmigosTask, actualiza el mapa con los amigos recibidos por el GET
        @Override
        protected void onPostExecute(List<Amigo> amigoList) {
            if (amigoList != null) {
                addListToMap(amigoList);
            } else {
                Toast.makeText(MainActivity.this, "Error al recibir lista amigos", Toast.LENGTH_SHORT).show();
            }
        }
        //Metodo que hace peticion GET a la API
        private InputStream openUrl(String urlString) throws IOException {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            conn.connect();
            return conn.getInputStream();
        }
        //Metodo que devuelve una lista de objetos Amigo dada por la API
        private List<Amigo> parseDataFromNetwork(String data)
                throws IOException, JSONException {
            //Iniciamos la lista vacia
            List<Amigo> amigosList = new ArrayList<Amigo>();
            //Creamos el array de JSONObjects con la respuesta de la API
            JSONArray amigos = new JSONArray(data);
            //Recorremos el array por cada amigo
            for(int i = 0; i < amigos.length(); i++) {
                //Obtenemos el JSONObject correspondiente a la posicion
                JSONObject amigoObject = amigos.getJSONObject(i);
                //Obtenemos el string de cada parametro
                String id = amigoObject.getString("id");
                String name = amigoObject.getString("name");
                String longi = amigoObject.getString("longi");
                String lati = amigoObject.getString("lati");
                //Parseamos los numeros al tipo correspondiente
                int ID;
                double longiNumber;
                double latiNumber;
                try {
                    ID = Integer.parseInt(id);
                    longiNumber = Double.parseDouble(longi);
                    latiNumber = Double.parseDouble(lati);
                } catch (NumberFormatException nfe) {
                    continue;
                }
                //Creamos y añadimos el objeto Amigo a la lista
                amigosList.add(new Amigo(ID,name,longiNumber,latiNumber));
            }
            //Tras acabar el bucle retornamos la lista de Amigos
            return amigosList;
        }
    }
    //Clase llamada cuando salta el timer del polling
    class UpdateAmigoPosition extends TimerTask {
        public void run() {
            //Cuando salta el timer actualizamos el mapa
            new ShowAmigosTask().execute(AMIGOS_URL);
        }
    }
    //Clase para la tarea asincrona de actualizar la información de posición del usuario
    class SendLocationTask extends AsyncTask<String, Void, Amigo>{
        //doInBackground de SendLocationTask, manda petición POST o PUT a la api
        @Override
        protected Amigo doInBackground(String... urls) {
            try {
                String json = readStream(updateAmigo(urls[0],mUser));
                return parseAmigoData(json);
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                return null;
            }
        }
        //onPostExecute de SendLocationTask, actualiza el user con el recibido como respuesta
        @Override
        protected void onPostExecute(Amigo user) {
            if (user != null) {
                mUser = user;
            } else {
                Toast.makeText(MainActivity.this, "Error al actualizar tu información", Toast.LENGTH_SHORT).show();
            }
        }
        //Metodo con el que mandaremos las peticiones POST o PUT para el envio de informacion de nuestro usuario
        protected InputStream updateAmigo(String URL, Amigo User) throws IOException, JSONException {
            //Obtenemos la URL final
            String api_url = URL;
            //Ponemos PUT como metodo estatico
            String metodo = "PUT";
            //Inicialiamos el JSONObject para el body
            JSONObject jsonObject = new JSONObject();
            //Si ya se ha mandado petición POST y hemos recibido el usuario
            if (User.ID != 0 && isUserCreated) {
                //Introducimos el id en el body ya que lo necesita el metodo PUT de la API
                jsonObject.put("id", User.ID);
                //Introducimos tambien el id en la URL
                String id = String.valueOf(User.ID);
                api_url = URL + "/" + id;
            //Todavia no se ha mandado el POST inicial
            } else if(!isUserCreated){
                //Cambiamos el metodo de la peticion a POST e indicamos que ya se ha mandado
                metodo = "POST";
                isUserCreated = true;
            }
            //Introducimos el resto de parametros al Body
            jsonObject.put("name", User.name);
            jsonObject.put("lati", User.lati.toString());
            jsonObject.put("longi", User.longi.toString());
            //Parseamos el JSONObject a String
            String jObjSent = jsonObject.toString();

            //Conexión a la api con la URL, metodo y Body corresposdientes
            URL url = new URL(api_url);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setReadTimeout(10000 /* milliseconds */);
            httpCon.setConnectTimeout(15000 /* milliseconds */);

            httpCon.setDoOutput(true);
            httpCon.setDoInput(true);
            httpCon.setRequestProperty("Content-Type","application/JSON");

            httpCon.setRequestMethod(metodo);
            OutputStreamWriter out = new OutputStreamWriter(
                    httpCon.getOutputStream());

            out.write(jObjSent);
            out.close();
            //Retornamos InputStream
            return httpCon.getInputStream();
        }
        //Metodo que devuelve un objeto Amigo recibido de peticion PUT o POST
        private Amigo parseAmigoData(String data) throws IOException, JSONException {
            //Creamos el JSONObject con el string de la respuesta
            JSONObject amigoObject = new JSONObject(data);
            //Obtenemos cada uno de sus parametros en String
            String id = amigoObject.getString("id");
            String name = amigoObject.getString(("name"));
            String lon = amigoObject.getString(("longi"));
            String la = amigoObject.getString("lati");
            //Parseamos los numeros al tipo adecuado
            int ID;
            double longiNumber;
            double latiNumber;
            ID = Integer.parseInt(id);
            longiNumber = Double.parseDouble(lon);
            latiNumber = Double.parseDouble(la);
            //Retornamos el amigo
            return new Amigo(ID,name,longiNumber,latiNumber);
        }
    }
}