package com.vodafone.paulabohorquez.geomessaging;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;


import android.net.wifi.WifiManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Button;
import android.widget.EditText;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.String;
import java.math.BigInteger;
import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import static android.R.attr.longClickable;
import static android.R.attr.type;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    /*----------------Initialize Variables - MAPS ----------------*/
    private GoogleMap mMap;
    public Boolean ready;
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    Marker mCurrentLocation;
    Location mLastLocation;
    /*---------------Initialize Variables - NETWORK----------------*/
    public int mcc;
    public int mnc;
    public int lac;
    public int cid;
    public int pastCellID;
    public long globalCellID;
    public boolean changed;
    public String temp;
    public String tempint;


    /*---------------Initialize Variables - MSG ---------------------*/

    private Handler handler = new Handler();
    public ListView msgView;
    public ArrayAdapter<String> msgList;
    protected int PORTDENM = 8006;//5432;
    protected int PORTCAM = 8005;//5433;
    private Toolbar toolbar;
    private static String MCAST_ADDR = "FF1E::0"; //Default address
    public String insertedcidValue;

    private static InetAddress GROUP;
    private MulticastSocket mcSocketCam;
    private MulticastSocket mcSocketDenm;
    private DatagramPacket mcPacketSend;
    volatile boolean startedApp = false;
    public TelephonyManager telephony;
    volatile boolean insertedCid = false;

    public MainActivity() throws IOException {
        System.out.println("Could not set up the sockets");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = (Toolbar) findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.vodalogo);
        //Set Notification bar color
        Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this,R.color.colorPrimaryDark));



        /*-------------GOOGLE MAPS Initialization--------------------*/
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        /*---------------List of messages initialization - MSG-----------------*/
        msgView = (ListView) findViewById(R.id.listView);
        msgList = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1);
        msgView.setAdapter(msgList);

        /*-----Telephony Manager initialization-----*/
        telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephony.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            final GsmCellLocation gsmlocation = (GsmCellLocation) telephony.getCellLocation();
            if (gsmlocation != null) {
                pastCellID = gsmlocation.getCid();
                System.out.println("Cell ID:"+pastCellID);
                try {
                    getGroupAddr();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                System.out.println("This is my IP Address:     "+MCAST_ADDR);
            }
        }

    }


    /*--------------------------------GOOGLE MAPS METHODS------------------------------*/
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Initialize Google Play Services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
            }
        }
        else {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }
        mMap.getUiSettings().setZoomControlsEnabled(true);
        ready = true;
    }
    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        if (mCurrentLocation != null){
            mCurrentLocation.remove();
        }
        //Place my location Marker
        LatLng latitlong = new LatLng(location.getLatitude(), location.getLongitude());
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(latitlong)
                .zoom(17)
                .build();
        //Uncomment the following to animate Camera to follow always location:
        //mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    /*-------------Send messages with my current location-------*/
        if (startedApp) {
            sendMessage(createMessage(0, 5, "None").toString(), PORTCAM, mcSocketCam);
        }



        /*-----------Check if Cell changed, if so then recalculate Multicast IP address--------*/


        if (changedCellID()){

            try {
                mcSocketDenm.leaveGroup(GROUP);
                mcSocketCam.leaveGroup(GROUP);

            } catch (IOException e) {
                e.printStackTrace();
                Log.e("ChangedCellID","Error: Could not leave group");
            }

            try{
                mcSocketCam.joinGroup(InetAddress.getByName(getGroupAddr()));
                mcSocketDenm.joinGroup(InetAddress.getByName(getGroupAddr()));

            }catch (IOException e){
                e.printStackTrace();
                Log.e("ChangedCellID","Error: Could not join new group");
            }



        }
    }

    //======================================================
//Check if the Cell ID changed, when location changed.
    public boolean changedCellID(){
        if (telephony.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            final GsmCellLocation gsmlocation = (GsmCellLocation) telephony.getCellLocation();
            if (gsmlocation != null) {
                if (gsmlocation.getCid() == pastCellID){
                    changed = false;
                }
                else {
                    changed = true;
                }
            }
        }

        return changed;
    }
    //Calculate IP Multicast Address
    public String getGroupAddr () throws UnknownHostException {

        if (insertedCid){
            tempint = insertedcidValue;
        }
        else {
            String sub1;
            String sub2;
            GsmCellLocation gsmlocation = (GsmCellLocation) telephony.getCellLocation();
            String networkOperator = telephony.getNetworkOperator();
            if (gsmlocation != null && networkOperator != null ) {
                lac = gsmlocation.getLac();
                lac = 65534;
                mcc = Integer.parseInt(telephony.getNetworkOperator().substring(0, 3));
                mnc = Integer.parseInt(telephony.getNetworkOperator().substring(3));
                cid = gsmlocation.getCid();
                tempint = "" + mcc + mnc + lac + cid;
            }

            System.out.print("This is my tempint   "+tempint);
            globalCellID = Long.parseLong(tempint);
            String toHex = Long.toHexString(Long.parseLong(tempint));


            sub1 = toHex.substring(0,toHex.length()%4);
            for(int i = toHex.length()%4; i<toHex.length(); i+=4){
                sub2 = toHex.substring(i,i+4);
                sub1 = sub1 + ":"+sub2;
            }
            MCAST_ADDR = "FF1E::"+ sub1;
            System.out.print("This is my new address after LONG conv  "+MCAST_ADDR);
        }
        //TODO: wenn keine reale CellID benutzt werden soll sollte die MCAST_ADDR wieder auf den default wert gesetzt werden
        InetAddress ipAdd = InetAddress.getByName(MCAST_ADDR);

        if (!ipAdd.isMulticastAddress()){
            MCAST_ADDR = "FF1E::0"; //Assign default Address
        }



        return MCAST_ADDR;
    }

    //==================================================

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest,this);
        }

        /*---------------Initialize buttons - MAPS ---------------------*/
        final Button accidentButton = (Button) findViewById(R.id.accidentButton);
        final Button stopButton = (Button) findViewById(R.id.stopButton);
        final Button startButton = (Button) findViewById(R.id.startButton);

        /*-------------------Setting on-click listener for all buttons----------*/

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startedApp = true;
                //Create CAM and DENM sockets
                createSockets(MCAST_ADDR);
                //Allow reception from messages in two different threads
                try {
                    receiveMessageCAM();
                    receiveMessageDENM();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startedApp = false;
                //Leave multicast group for the two sockets
                try {
                    mcSocketDenm.leaveGroup(GROUP);
                    mcSocketDenm.close();

                    mcSocketCam.leaveGroup(GROUP);
                    mcSocketCam.close();

                } catch (IOException e) {
                    e.printStackTrace();

                    Log.e("Stop Button","Error leaving group and/or closing socket");
                }

                if (mcSocketCam.isClosed()){
                    System.out.print("CAM Socket succesfully closed!");
                }
                if(mcSocketDenm.isClosed()){
                    System.out.print("DENM Socket succesfully closed!");
                }


            }
        });


        accidentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: Send message for TimeToliveTime. ES sollen für z.B. 15 sec die DENM jede seconde wieder versendet werden.
                showttlDialog();




            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    protected synchronized void buildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {


                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);


            } else {
                // No explanation needed, request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // Permission Granted
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                } else {

                    // Permission denied, Disable the functionality that depends on this permission.
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

/*-----------------------------MESSAGING METHODS---------------------------------*/


    public JSONObject createMessage(int type, long timetolive, String message){


         /*---------------Creating JSON Object-------------------*/
        final JSONObject myJO = new JSONObject();
        JSONArray jarr = new JSONArray();
        jarr = jarr.put(globalCellID);
        //jarr.put(0);
        //JSON Object to store data
        try {
            myJO.put("CreationTime", System.currentTimeMillis());
            myJO.put("Long",mLastLocation.getLongitude());
            myJO.put("Lat",mLastLocation.getLatitude());
            myJO.put("LifeTime", timetolive);
            myJO.put("Message", message);
            myJO.put("MessageTypeID", type);
            myJO.put("CellID",jarr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return myJO;
    }

/*-------------------------Sending ------------------------------------------*/

    public void createSockets(String mcAddress) {
        if (mcSocketCam == null) {
            try {
                GROUP = InetAddress.getByName(mcAddress);
                mcSocketCam = new MulticastSocket(PORTCAM);
                mcSocketCam.setTimeToLive(10);
                NetworkInterface nif = NetworkInterface.getByName("tun0");
                if (null != nif) {
                    System.out.println("picking interface " + nif.getName() + " for CAM transmit");
                    mcSocketCam.setNetworkInterface(nif);
                }

                mcSocketCam.joinGroup(GROUP);

            } catch (Exception e) {
                Log.d("Error CAM socket: ", e.getMessage());
            }
        }

        if (mcSocketDenm == null) {
            try {
                GROUP = InetAddress.getByName(mcAddress);
                mcSocketDenm = new MulticastSocket(PORTDENM);
                mcSocketDenm.setTimeToLive(10);
                //Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                NetworkInterface nif = NetworkInterface.getByName("tun0");
                if (null != nif) {
                    mcSocketDenm.setNetworkInterface(nif);
                }

                mcSocketDenm.joinGroup(GROUP);
            } catch (Exception e) {
                Log.d("Error DENM socket: ", e.getMessage());
            }
        }
    }


    public void sendMessage(String message, int port, final MulticastSocket socketType ) throws IllegalArgumentException {
        if (message == null || message.length() == 0) {
            throw new IllegalArgumentException();
        }
        final int myport = port;
        final String mensaje = message;

        new Thread(new Runnable() {
            @Override
            public void run() {

//Build the Datagram Packet

                try {
                    mcPacketSend = new DatagramPacket(mensaje.getBytes(), mensaje.length(), GROUP, myport);
                } catch (Exception e) {
                    Log.v("Error creating packet: ", e.getMessage());
                }
//Send the packet
                try {
                    socketType.send(mcPacketSend);

                } catch (IOException e) {
                    System.out.println("There was an error sending the packet");
                    e.printStackTrace();
                }
                System.out.println("Server sent packet with msg: " + mensaje);
            }
        }).start();
    }

/*-------------------------Receiving -------------------------------------------*/

    public void receiveMessageCAM() throws UnknownHostException {
        giveLock();
        new Thread(new Runnable() {
            @Override
            public void run() {

                try {

                    byte[] buffer = new byte[256];
                    while (startedApp){
                        // Create a buffer of bytes, which will be used to store incoming messages

                        // Receive the info on the CAM Socket
                        DatagramPacket mcPacketRecv = new DatagramPacket(buffer, buffer.length);
                        mcSocketCam.receive(mcPacketRecv);
                        //Decide what to show with info received
                        String msg = new String(buffer, 0, buffer.length);
                        //System.out.print("Received:" + msg);
                        interpretMessage(msg);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }).start();

    }


    public void receiveMessageDENM() throws UnknownHostException {
        giveLock();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    while (startedApp){
                        // Create a buffer of bytes, which will be used to store incoming messages
                        byte[] buffer = new byte[256];
                        // Receive the info on a socket and print it on the screen
                        DatagramPacket mcPacketRecv2 = new DatagramPacket(buffer, buffer.length);
                        mcSocketDenm.receive(mcPacketRecv2);
                        String msg = new String(buffer, 0, buffer.length);
                        System.out.print("Received DENM: " +msg);
                        interpretMessage(msg);

                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

    public void interpretMessage (String msgReceived){
        try {
            JSONObject rjsonObj = new JSONObject(msgReceived);
            //Get keys and values to use in the future
            Double Lati = rjsonObj.getDouble("Lat");
            Double Longi = rjsonObj.getDouble("Long");
            String Message = rjsonObj.getString("Message");
            int MessageType = rjsonObj.getInt("MessageTypeID");
            Long TimeToLive = (long) (rjsonObj.getDouble("LifeTime"))*1000;
            String situation = "Null";

            switch (MessageType){
                //Position Daten
                case 0:
                    situation = "red";

                    break;
                //Unfall
                case 1:
                    situation = "accident";

                    break;
                //Stau
                case 2:
                    situation="trafficjam";

                    break;
                //Speedlimit
                case 3:
                    situation="maxspeed";
                    break;
                //Error
                default:
                    System.out.println("Error: Incompatible message type");
                    break;
            }
            //Display icon on map and message received on the screen
            displayMarker(Lati,Longi,TimeToLive,situation);
            if (!(Message.equals("0") || Message.equals("None"))) {
                displayMsg(Message);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //In some android devices, where is unabled by default used to allow incoming Multicast Messages.
    public void giveLock () {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock multicastLock = wifi.createMulticastLock("multicastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();
    }

/*-----------------------Display my message--------------------------------*/

    public void displayMsg(String msg) {
        if (startedApp) {
            final String mensajeRecibido = msg;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    msgList.add(mensajeRecibido);
                    msgView.setAdapter(msgList);
                    msgView.smoothScrollToPosition(msgList.getCount() - 1);
                }
            });
        }
    }

    /*------------Display a Marker on the Map----------*/
    public void displayMarker(final Double Lati, final Double Longi, long TTL, String type){
        final long TimeToLive = TTL;
        final BitmapDescriptor myicon;
        int id = getResources().getIdentifier(type, "drawable", getPackageName());
        myicon = BitmapDescriptorFactory.fromResource(id);

        runOnUiThread(new Runnable(){
            @Override
            public void run(){
                LatLng pos = new LatLng(Lati,Longi);
                Marker mar = mMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .icon(myicon)
                );
                fadeTime(TimeToLive,mar);
            }

        });
    }

/*-----Customize characteristics of the markers: Size and time to fade--------*/


    public void fadeTime(long duration, Marker marker) {

        final Marker myMarker = marker;
        ValueAnimator myAnim = ValueAnimator.ofFloat(1, 0);
        myAnim.setDuration(duration);
        myAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                myMarker.setAlpha((float) animation.getAnimatedValue());
            }
        });
        myAnim.start();
    }
    /*------------------------ANDROID ACTIVITY METHODS------------*/
    @Override
    public void onPause() {
        super.onPause();

        //stop location updates when Activity is no longer active
        if (mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if (mGoogleApiClient != null &&
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            mcSocketCam.leaveGroup(GROUP);
            mcSocketCam.close();


            mcSocketDenm.leaveGroup(GROUP);
            mcSocketDenm.close();


        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_settings:
                showSettingsDialog();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
    //Display settings dialog for changing Cell ID and rejoining group

    public void showSettingsDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Get the layout inflater
        LayoutInflater inflater = this.getLayoutInflater();
        final View dView = inflater.inflate(R.layout.dialog_settings, null);
        builder.setView(dView);
        final EditText cidInput =(EditText) dView.findViewById(R.id.cidInput);



        builder        .setPositiveButton(R.string.continues, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                insertedCid = true;
                insertedcidValue = cidInput.getText().toString();
                if (!insertedcidValue.isEmpty()) {
                    try {
                        mcSocketDenm.leaveGroup(GROUP);
                        mcSocketCam.leaveGroup(GROUP);

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e("ChangedCellID", "Error: Could not leave group");
                    }

                    try {
                        mcSocketCam.joinGroup(InetAddress.getByName(getGroupAddr()));
                        mcSocketDenm.joinGroup(InetAddress.getByName(getGroupAddr()));

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e("ChangedCellID", "Error: Could not join new group");
                    }
                    insertedCid = false;
                }

            }
        })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        dialog.dismiss();

                    }
                });
        AlertDialog ad = builder.create();
        ad.show();
    }

    public void showttlDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Get the layout inflater
        LayoutInflater inflater = this.getLayoutInflater();
        final View dView = inflater.inflate(R.layout.dialog_ttl, null);
        builder.setView(dView);
        final EditText ttlInput =(EditText) dView.findViewById(R.id.ttlInput);


        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        // Add action buttons
        builder        .setPositiveButton(R.string.continues, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                long myttl;

                if (!ttlInput.getText().toString().isEmpty()){
                    myttl = Long.parseLong(ttlInput.getText().toString());
                }
                else {myttl = 15;} //(Default)

                sendMessage(createMessage(1, myttl, "Accident reported").toString(), PORTDENM, mcSocketDenm);

            }
        })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        AlertDialog ad = builder.create();
        ad.show();
    }


}



