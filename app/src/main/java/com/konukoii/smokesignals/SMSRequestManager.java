package com.konukoii.smokesignals;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationListener;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.gsm.SmsMessage;
import android.util.Log;
import android.widget.Toast;
import android.telephony.SmsManager;
import java.util.ArrayList;
import java.util.Date;
import android.content.IntentFilter;
import android.provider.CallLog;
import android.database.Cursor;
import android.content.ContentResolver;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.PhoneLookup;
import android.content.ContentResolver;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import java.util.Calendar;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;

/**
 * Created by TransAtlantic on 2/14/2015.
 */



public class SMSRequestManager {

    //Debuggin' Purpouses
    private final static String TAG="SmokeSignals";

    //Void Read from file the messageCue

    private final static int LOCATION = 1;
    private final static int CONTACTSEARCH = 2;
    private final static int MISSEDCALLS = 3;
    private final static int BATTERYLIFE = 4;
    private final static int RING = 5;
    private final static int HELP = 6;

    private final static String HELP_TXT = "TEXT ME:\n'//Location' <- To query GPS coordinates\n" +
                                                    "'//Contact [name]' <- For contact search\n" +
                                                    "'//Calls' <- To query missed calls\n" +
                                                    "'//Battery' <-To query battery life\n"+
                                                    "'//Ring' <-For phone to start ringing (for 2 Minutes)\n"+
                                                    "'//Help' <-To display this help menu again\n";


    Context context;    //The context that called this
    Intent intent;      //The intent that called this
    String msg_from;    //Who is the app talking to
    MediaPlayer ringerPlayer; //The MediaPlayer for the Ringer

    //Void Go
        //Main thing from where everything stems from
    public void go(Context context, Intent intent){
        this.context = context;
        this.intent = intent;

        Log.d(TAG, "New SMS Arrived");

        Bundle bundle = intent.getExtras();
        SmsMessage[] msgs = null;
        msg_from="";
        String msg_body="";
        if (bundle != null) {
            try {
                Object[] pdus = (Object[]) bundle.get("pdus");
                msgs = new SmsMessage[pdus.length];
                for (int i = 0; i < msgs.length; i++) {
                    msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    msg_from = msgs[i].getOriginatingAddress();
                    msg_body = msgs[i].getMessageBody();
                }



                parseSMS(msg_body);




            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }
    }

    //ParseCmd
    private int parseSMS(String msg_body){
        if (msg_body.equals("//Location")){
            Toast.makeText(context, "Location?", Toast.LENGTH_LONG).show();
            QueryLocation();
            return LOCATION;
        }
        else if (msg_body.equals("//Ring")){
            Toast.makeText(context, "Ring?", Toast.LENGTH_LONG).show();
            QueryRing();
            return RING;
        }
        else if (msg_body.equals("//Battery")){
            Toast.makeText(context, "Battery?", Toast.LENGTH_LONG).show();
            QueryBattery();
            return BATTERYLIFE;
        }
        else if (msg_body.equals("//Calls")){
            Toast.makeText(context, "Calls?", Toast.LENGTH_LONG).show();
            QueryMissedCalls();
            return MISSEDCALLS;
        }
        else if (msg_body.equals("//Help")){
            Toast.makeText(context, "Help?", Toast.LENGTH_LONG).show();
            QueryHelp();
            return HELP;
        }
        else if (msg_body.substring(0,9).equals("//Contact")){
            Toast.makeText(context, "Contact?", Toast.LENGTH_LONG).show();
            QueryContact(msg_body.substring(10));
            return CONTACTSEARCH;
        }


        return 0;
    }


    //Respond_to_SMS
    private void sendSMS(String phoneNumber, String message){

        SmsManager sms = SmsManager.getDefault();
        ArrayList<String> parts = sms.divideMessage(message);
        sms.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
        //sms.sendTextMessage(phoneNumber, null, message, null, null);
        Log.d(TAG,"message sent!");
    }

    //Query Functions//////////////////////////////////////////////////////////////////////////////
    private void QueryHelp(){sendSMS(msg_from, HELP_TXT);}

    private void QueryBattery(){context.registerReceiver(this.BatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));}

    private void QueryLocation(){
        GPSLocation gpsloc = new GPSLocation();
        gpsloc.go();
    }

    private void QueryMissedCalls(){

        //Get all the Call Log
        String[] projection = { CallLog.Calls.CACHED_NAME, CallLog.Calls.CACHED_NUMBER_LABEL, CallLog.Calls.TYPE };

        //Query to find which calls in the Call Log are MISSED and NEW (Haven't been awknoledged by user)
        String where = CallLog.Calls.TYPE+"="+CallLog.Calls.MISSED_TYPE+" AND NEW = 1";
        Cursor c = context.getContentResolver().query(CallLog.Calls.CONTENT_URI,null,where, null, null);

        //Check if there's no missed calls...or negative missed calls? :S
        if (c.getCount() <=0){
            sendSMS(msg_from,"No missed calls...no she didn't call back...yes its because she found you weird..."); //lulz remember to change this
        }

        //Make a nice list of missed calls (Hopefully you don't have 42 missed phone calls from you girlfriend in the last hour)
        c.moveToFirst();



        String output="MISSED CALLS:";
        int number = c.getColumnIndex(CallLog.Calls.NUMBER);
        int name = c.getColumnIndex(CallLog.Calls.CACHED_NAME);
        int date = c.getColumnIndex(CallLog.Calls.DATE);

        do{ //Because you know you have at least one

            String phNumber = c.getString(number);
            String callDate = c.getString(date);
            String callerName = c.getString(name);
            Date callDayTime = new Date(Long.valueOf(callDate));

            output+="\nName: "+callerName+"\nPhone Number: " + phNumber +"\nCall Date: " + callDayTime;
            output+="\n-------";

        }while(c.moveToNext());

        sendSMS(msg_from,output);

        Log.d(TAG, output); //do some other operation

    }

    private void QueryContact(String query){

        /*
         Explanation for this module:

            Reference Links: http://stackoverflow.com/questions/9625308/android-find-a-contact-by-display-name

         */

        //DO NOT DELETE THIS. Believe me, I have seen what happens when you indiscriminatly query for 'a' and suddenly your phone is dumping all your contacts!
        if (query.length()<=2){
            sendSMS(msg_from,"Query is too short. Please provide a contact query at least of at 3 characters");
            return;
        }

        //First get the List of All possible Contacts with their IDs
        String _query = query;
        query = "%"+query+"%"; //Super important for the SQL LIKE command (LIKE %ed% returns true to EDuardo, pEDro, etc. (also LIKE is not case sensitive!)
        String id_name=null;
        Uri resultUri = ContactsContract.Contacts.CONTENT_URI;
        Cursor cont = context.getContentResolver().query(resultUri, null, null, null, null);
        String whereName = ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME + " LIKE ?" ;
        String[] whereNameParams = new String[] { ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,query};

        Cursor nameCur = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, whereName, whereNameParams, StructuredName.CONTACT_ID);

        String output = "Possible Matches:\n";

        //We are going to store phones here, so we can check that we don't print duplicate info
        //This is necessary because Android is a mess when it comes to storing contacts
        //So don't be surprised if you have tooooons of weird duplicates
        ArrayList<String> contacts = new ArrayList<String>();

        //No Contact Found
        if (nameCur.getCount()<=0){ sendSMS(msg_from,"No contact found matching "+_query);}

        //Loop through the IDs
        while (nameCur.moveToNext()) {

            String name="";
            String phone="";
            String email="";
            String id="";

            //USE THE IDs you got to find the rest of the information
            id = nameCur.getString(nameCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID));

            //FIND NAME;
            name = nameCur.getString(nameCur.getColumnIndex(StructuredName.DISPLAY_NAME));

            //FIND NUMBER
            whereName = ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID + " = ?";
            String[] whereNameParams2 = new String[]{ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, id};
            Cursor nameCur2 = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, whereName, whereNameParams2, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
            while (nameCur2.moveToNext()) {
                phone = nameCur2.getString(nameCur2.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            }
            nameCur2.close();

            //FIND EMAIL
            String[] whereNameParams3 = new String[]{ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, id};
            Cursor nameCur3 = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, null, whereName, whereNameParams3, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
            while (nameCur3.moveToNext()) {
                email = nameCur3.getString(nameCur3.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
            }
            nameCur3.close();

            //CHECK FOR DUPLICATES
            boolean flag=false;
            for (int i=0;i<contacts.size();i++){
                if (contacts.get(i).equals(phone)){flag=true;}
            }
            if (flag==true){continue;};

            contacts.add(phone);

            //PRINTOUT
            if (phone != "") {
                output += "\nName: " + name;
                output += "\nPhone: " + phone;
                if (email != "") {
                    output += "\nEmail: " + email;
                }
                output += "\n-----";
            }
        }

        //Since we are explicitly not allowing searching for contacts that only have emails associated with them
        if (output.equals("Possible Matches:\n")){ sendSMS(msg_from,"No contact found matching "+_query);return;}

        sendSMS(msg_from, output);

    }

    private void QueryRing(){

        //Si te vas por ringtone la broma no hace looping
        //Si te vas por MediaPlayer la broma insiste en crashea
        //Cuando decifres esto, montas un broadcast reciever para ACTION_USER_PRESENT para apagar el sonido
        //mosca que dejaste un MediaPlayer variable declarado por all'a arriba :)
        //chamo si me provoca una arepa y un papelon con limon ahorita

        //1. Get Alarm Sound
        Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if(alert == null){
            // alert is null, using backup
            alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            // I can't see this ever being null (as always have a default notification)
            // but just incase
            if(alert == null) {
                // alert backup is null, using 2nd backup
                alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
        }
        //2. Raise Volume
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = audioManager.getStreamMaxVolume(audioManager.STREAM_ALARM);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM,maxVolume,0);

        //3. Play Alarm
        final Ringtone r = RingtoneManager.getRingtone(context, alert);
        r.play();

        /*
        try {
            ringerPlayer = new MediaPlayer();
            ringerPlayer = MediaPlayer.create(context, alert);
            ringerPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            ringerPlayer.setLooping(true);
            ringerPlayer.prepare();
            ringerPlayer.start();
        }catch (Exception e){ Log.d(TAG,e.getMessage());}
        */


    }
//////Broadcast Receivers and Listeners Inner Classes///////////////////////////////////////////////
    /*Broadcasters/Listeners take time to awnser. (you can think of them as separate processes.
    //You call them by registering them to the service and when you are done you unregister them.
    //However the service doesn't know when these guys are done doing their thing, so they
    //gotta be inner classes to be able to unregister themselves
    */

    //Battery Broadcast Reciever
    private BroadcastReceiver BatteryReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent intent) {
            int level = intent.getIntExtra("level", 0);
            sendSMS(msg_from,"Battery Level: "+level+"%");
            Log.d(TAG,"Sent Battery Level");
            context.unregisterReceiver(this);
        }
    };

    //GPS Location Listener
    public class GPSLocation implements LocationListener{

        /*
            God Bless whoever asked this question: http://stackoverflow.com/questions/10524381/gps-android-get-positioning-only-once
         */

        LocationManager mLocationManager;

        public void go(){
            mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Location location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            //Get Last Known location (2 minutes old max) [Lowers battery consumption]
            if(location != null && location.getTime() > Calendar.getInstance().getTimeInMillis() - 2 * 60 * 1000) {
                Log.d(TAG, location.getLatitude() + " and " + location.getLongitude());
                String output = "Location:\n"+"Lat: "+location.getLatitude() + " Long: "+
                        location.getLongitude()+"\nGmaps: "+" http://google.com/maps/?q="+location.getLatitude()+","+location.getLongitude();

                sendSMS(msg_from,output);
                mLocationManager.removeUpdates(this); //Super Important to RemoveUpdates (only want to query once)
            }
            else {
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)==true) {
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                }else{
                    sendSMS(msg_from,"GPS is Turned Off! Can't Report Location :'(");
                }
            }
        }

        @Override
        public void onLocationChanged(Location location){
            if (location !=null){
                Log.d(TAG, location.getLatitude() + " and " + location.getLongitude());
                String output = "Location:\n"+"Lat: "+location.getLatitude() + " Long: "+
                        location.getLongitude()+"\nGmaps: "+" http://google.com/maps/?q="+location.getLatitude()+","+location.getLongitude();

                sendSMS(msg_from,output);
                mLocationManager.removeUpdates(this); //Super Important to RemoveUpdates (only want to query once)
            }
        }
        // Required functions
        @Override
        public void onProviderDisabled(String arg0) {;}
        @Override
        public void onProviderEnabled(String arg0) {;}
        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {;}
    }

////////////////////////////////////////////////////////////////////////////////////////////////////

}
