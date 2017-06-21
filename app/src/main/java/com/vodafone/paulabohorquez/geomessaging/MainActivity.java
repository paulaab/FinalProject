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
import android.support.v4.util.TimeUtils;
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
import android.widget.CompoundButton;
import android.widget.TextView;
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
import android.widget.ToggleButton;

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
import java.util.concurrent.TimeUnit;

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
    public volatile int pastCellID;
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
    public volatile InetAddress MCAST_ADDR; //Default address
    public String insertedcidValue;
    private String DEFAULTADDR = "FF1E::0";

    //private volatile InetAddress GROUP;
    private MulticastSocket mcSocketCam;
    private MulticastSocket mcSocketDenm;
    private DatagramPacket mcPacketSend;
    public volatile boolean startedApp = false;
    public volatile boolean setDefault;
    public boolean startedOnce = false;
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


        try {
            insertedCid = false;
            getMCGroupAddr(setDefault = true);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.out.print("Could not se default Mcast Address");
        }

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
                    insertedCid = false;
                    getMCGroupAddr(setDefault = false);
                    System.out.println("This is my IP Address:     "+MCAST_ADDR);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }

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
            sendMessage(createMessage(0,10, "None").toString(), PORTCAM, mcSocketCam);
        }



        /*-----------Check if Cell changed, if so then recalculate Multicast IP address--------*/


        if (changedCellID()){
            if (!insertedCid){
                try {
                    mcSocketDenm.leaveGroup(MCAST_ADDR);
                    mcSocketCam.leaveGroup(MCAST_ADDR);

                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("ChangedCellID","Error: Could not leave past group");
                }

                try{
                    getMCGroupAddr(setDefault = false);
                    mcSocketCam.joinGroup(MCAST_ADDR);
                    mcSocketDenm.joinGroup(MCAST_ADDR);

                }catch (IOException e){
                    e.printStackTrace();
                    Log.e("ChangedCellID","Error: Could not join new group");
                }

            }





        }
    }

    //======================================================
//Check if the Cell ID changed, when location changed.
    public boolean changedCellID(){
        if (!insertedCid) {
            if (telephony.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
                final GsmCellLocation gsmlocation = (GsmCellLocation) telephony.getCellLocation();
                if (gsmlocation != null) {
                    if (gsmlocation.getCid() == pastCellID) {
                        changed = false;
                    } else {
                        changed = true;
                        pastCellID = gsmlocation.getCid();
                    }
                }
            }
        }
        return changed;
    }
    //Calculate IP Multicast Address
    public InetAddress getMCGroupAddr (boolean setDefaultAddress) throws UnknownHostException {

        String sub1;
        String sub2;

        if (!setDefaultAddress) {

            GsmCellLocation gsmlocation = (GsmCellLocation) telephony.getCellLocation();
            String networkOperator = telephony.getNetworkOperator();

            if (gsmlocation != null && networkOperator != null) {
                lac = gsmlocation.getLac();
                lac = 65534;
                mcc = Integer.parseInt(telephony.getNetworkOperator().substring(0, 3));
                mnc = Integer.parseInt(telephony.getNetworkOperator().substring(3));

                if (insertedCid) {
                    cid = Integer.parseInt(insertedcidValue);
                    pastCellID = cid;
                    System.out.println("The new added CID is: " + cid);
                } else {

                    cid = gsmlocation.getCid();

                }
            }
        }
            else {
                //MCAST_ADDR = InetAddress.getByName(DEFAULTADDR);
            cid = 0;
            pastCellID = cid;
            lac = 0;
            mcc = 0;
            mnc = 0;
            }


                tempint = "" + mcc + mnc + lac + cid;
               // System.out.print("This is my tempint   "+tempint);
                globalCellID = Long.parseLong(tempint);
                String toHex = Long.toHexString(globalCellID );


                sub1 = toHex.substring(0,toHex.length()%4);
                for(int i = toHex.length()%4; i<toHex.length(); i+=4){
                    sub2 = toHex.substring(i,i+4);
                    sub1 = sub1 + ":"+sub2;
                }
                tempint = "FF1E::"+ sub1;
                System.out.print("\nThis is my new address after LONG conv  "+tempint);
                MCAST_ADDR = InetAddress.getByName(tempint);



        //TODO: wenn keine reale CellID benutzt werden soll sollte die MCAST_ADDR wieder auf den default wert gesetzt werden
        //InetAddress ipAdd = InetAddress.getByName(MCAST_ADDR);

        if (!MCAST_ADDR.isMulticastAddress()){
          MCAST_ADDR = InetAddress.getByName(DEFAULTADDR); //Assign default Address
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
        ToggleButton toggle = (ToggleButton) findViewById(R.id.togglebutton);
        toggle.setBackgroundColor(getResources().getColor(R.color.lightGreen));


        /*--------------Set onClick Listeners for the buttons---------------*/
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    buttonView.setBackgroundColor(getResources().getColor(R.color.lightBrown));

                    startedApp = true;
                    startedOnce = true;
                    //Create CAM and DENM sockets

                    createSockets();

                    //Allow reception from messages in two different threads
                    try {
                        receiveMessageCAM();
                        receiveMessageDENM();
                        Toast.makeText(MainActivity.this,"You are connected now!",Toast.LENGTH_LONG).show();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this,"An error occured, please try again",Toast.LENGTH_LONG).show();
                    }

                } else {
                    // The toggle is disabled
                    buttonView.setBackgroundColor(getResources().getColor(R.color.lightGreen));

                    if (startedOnce) {
                        startedApp = false;
                    Log.e("Stop Button","STARTEDAPP : " + startedApp);
                    try {
                        TimeUnit.SECONDS.sleep(2);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                    //Leave multicast group for the two sockets
                        try {

                            mcSocketDenm.leaveGroup(MCAST_ADDR);
                            mcSocketDenm.close();


                            mcSocketCam.leaveGroup(MCAST_ADDR);
                            mcSocketCam.close();

                            Log.e("Stop Button","Sockets closed");

                            if (mcSocketCam.isClosed()){
                                System.out.print("CAM Socket succesfully closed!\n");
                                mcSocketCam = null;
                            }
                            if(mcSocketDenm.isClosed()){
                                System.out.print("DENM Socket succesfully closed!\n");
                                mcSocketDenm = null;
                            }
                            Toast.makeText(MainActivity.this,"Disconnected from the network!\n",Toast.LENGTH_LONG).show();


                        } catch (IOException e) {
                            e.printStackTrace();

                            Log.e("Stop Button","Error leaving group and/or closing socket");
                        }


                        startedApp = false;





                    }
                    else {
                        Toast.makeText(MainActivity.this,"Try to connect first!",Toast.LENGTH_LONG).show();
                    }

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

    public void createSockets() {
        if (mcSocketCam == null) {
            try {
                insertedCid = false;
                getMCGroupAddr(setDefault = false);
                mcSocketCam = new MulticastSocket(PORTCAM);
                mcSocketCam.setTimeToLive(10);
                NetworkInterface nif = NetworkInterface.getByName("tun0");
                if (null != nif) {
                    System.out.println("picking interface " + nif.getName() + " for CAM transmit");
                    mcSocketCam.setNetworkInterface(nif);
                }

                mcSocketCam.joinGroup(MCAST_ADDR);

            } catch (Exception e) {
                Log.d("Error CAM socket: ", e.getMessage());
            }
        }

        if (mcSocketDenm == null) {
            try {
                insertedCid = false;
                getMCGroupAddr(setDefault = false);
                mcSocketDenm = new MulticastSocket(PORTDENM);
                mcSocketDenm.setTimeToLive(10);
                //Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                NetworkInterface nif = NetworkInterface.getByName("tun0");
                if (null != nif) {
                    mcSocketDenm.setNetworkInterface(nif);
                }

                mcSocketDenm.joinGroup(MCAST_ADDR);
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
                    mcPacketSend = new DatagramPacket(mensaje.getBytes(), mensaje.length(), MCAST_ADDR, myport);
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
                System.out.print("Mcast addr to send:  "+MCAST_ADDR+"\n");
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
                    while (startedApp) {
                        //System.out.print("\nINSIDE WHILE RECEIVEMSG CAM\n");
                        // Create a buffer of bytes, which will be used to store incoming messages
                        if (!mcSocketCam.isClosed()){
                          //  System.out.print("\nRecvmsgcam:  Socket not closed yet\n");
                            // Receive the info on the CAM Socket
                            DatagramPacket mcPacketRecv = new DatagramPacket(buffer, buffer.length);
                            mcSocketCam.receive(mcPacketRecv);
                            //Decide what to show with info received
                            String msg = new String(buffer, 0, buffer.length);
                            Log.e("receiveMessageCAM", "Received following: "+msg+"\n"+"from: "+mcPacketRecv.getAddress());
                            //System.out.print("Received:" + msg);
                            interpretMessage(msg);

                        }

                    }
                    System.out.print("Receive CAM outside loop");
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
                        if(!mcSocketDenm.isClosed()){
                            // Receive the info on a socket and print it on the screen
                            DatagramPacket mcPacketRecv2 = new DatagramPacket(buffer, buffer.length);
                            mcSocketDenm.receive(mcPacketRecv2);
                            String msg = new String(buffer, 0, buffer.length);
                            System.out.print("Received DENM: " +msg);
                            interpretMessage(msg);

                        }


                    }
                    System.out.print("Receive DENM outside loop");
                } catch (IOException ex) {
                    startedApp = false;
                    System.out.print("Startedapp FALSE DENM: \n");
                    ex.printStackTrace();
                    System.out.print("STARDED APP DENM:  " + startedApp);
                }
            }
        }).start();
    }

    public void interpretMessage (String msgReceived){

        if(startedApp){
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
            mcSocketCam.leaveGroup(MCAST_ADDR);
            mcSocketCam.close();


            mcSocketDenm.leaveGroup(MCAST_ADDR);
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
            case R.id.action_info:
                showInfoDialog();
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
                    if (insertedcidValue.length() >= 4){


                        new Thread(new Runnable(){
                            @Override
                            public void run() {
                                try {
                                    mcSocketDenm.leaveGroup(MCAST_ADDR);
                                    mcSocketCam.leaveGroup(MCAST_ADDR);
                                    Log.e("ChangedCellID","Left group for both sockets: "+MCAST_ADDR);

                                } catch (IOException e) {
                                    e.printStackTrace();
                                    Log.e("ChangedCellID", "Error: Could not leave group");
                                }

                                try {
                                    getMCGroupAddr(setDefault = false);
                                    mcSocketCam.joinGroup(MCAST_ADDR);
                                    mcSocketDenm.joinGroup(MCAST_ADDR);
                                    Log.e("ChangedCellID","Joined group for both sockets: "+MCAST_ADDR+"\n");

                                } catch (IOException e) {
                                    e.printStackTrace();
                                    Log.e("ChangedCellID", "Error: Could not join new group");
                                }


                            }
                        }).start();

                        Toast.makeText(MainActivity.this, "New Cell ID assigned",Toast.LENGTH_LONG).show();
                       // Toast.makeText(MainActivity.this, "Using CELL ID: " + insertedcidValue + "\nMy current IP is: "+MCAST_ADDR,Toast.LENGTH_LONG).show();
                        //Toast.makeText(MainActivity.this, "My current IP is:  " + MCAST_ADDR,Toast.LENGTH_LONG).show();

                    }
                    else {
                        Toast.makeText(MainActivity.this, "CellID should at least contain 4 numbers!",Toast.LENGTH_LONG).show();
                    }

                }


            }
        })
                .setNegativeButton("Use default", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        insertedCid = false;

                        Thread thread = new Thread(new Runnable() {

                            @Override
                            public void run() {
                                try {
                                    mcSocketDenm.leaveGroup(MCAST_ADDR);
                                    mcSocketCam.leaveGroup(MCAST_ADDR);
                                    Log.e("ChangedCellID","Left group for both sockets: "+MCAST_ADDR+"\n");

                                } catch (IOException e) {
                                    e.printStackTrace();
                                    Log.e("ChangedCellID","Error: Could not leave group");
                                }

                                try{
                                    getMCGroupAddr(setDefault = true);
                                    mcSocketCam.joinGroup(MCAST_ADDR);
                                    mcSocketDenm.joinGroup(MCAST_ADDR);
                                    Log.e("ChangedCellID","Joined group for both sockets: "+MCAST_ADDR+"\n");

                                }catch (IOException e){
                                    e.printStackTrace();
                                    Log.e("ChangedCellID","Error: Could not join new group");
                                }

                            }
                        });

                        thread.start();
                        Toast.makeText(MainActivity.this, "Using default IP Address",Toast.LENGTH_LONG).show();

                        dialog.dismiss();

                    }
                })

                .setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }});


        AlertDialog ad = builder.create();
        ad.show();
    }


    public void showInfoDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Get the layout inflater
        LayoutInflater inflater = this.getLayoutInflater();
        final View diView = inflater.inflate(R.layout.dialog_info, null);
        builder.setView(diView);
        final TextView mycurrentip = (TextView) diView.findViewById(R.id.myip);
        final TextView mycurrentcell = (TextView) diView.findViewById(R.id.cellid);
        mycurrentip.setText(MCAST_ADDR.toString());
        mycurrentcell.setText(Integer.toString(pastCellID));
        //final EditText cidInput =(EditText) dView.findViewById(R.id.cidInput);



        builder        .setPositiveButton("Accept", new DialogInterface.OnClickListener() {
            @Override
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
        final EditText ttltimeInput =(EditText) dView.findViewById(R.id.ttltimeInput);


        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        // Add action buttons
        builder        .setPositiveButton(R.string.continues, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {

               new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long myttfade;
                        int numberofsending;

                        if (!ttlInput.getText().toString().isEmpty()&&!ttltimeInput.getText().toString().isEmpty()){
                            myttfade = Long.parseLong(ttlInput.getText().toString());
                            numberofsending = Integer.parseInt(ttltimeInput.getText().toString());

                        }
                        else {
                            myttfade = 5;
                            numberofsending = 15;
                        } //(Default)


                        while(numberofsending>0){
                            sendMessage(createMessage(1, myttfade, "Accident reported").toString(), PORTDENM, mcSocketDenm);
                            try {
                                TimeUnit.SECONDS.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            numberofsending = numberofsending-1;
                        }


                //sendMessage(createMessage(1, 15, "Accident reported").toString(), PORTDENM, mcSocketDenm);




                    }
                }).start();
                dialog.dismiss();




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



