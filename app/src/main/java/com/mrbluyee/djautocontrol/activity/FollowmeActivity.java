package com.mrbluyee.djautocontrol.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;


import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.AMap.OnMapClickListener;
import com.amap.api.maps2d.CameraUpdate;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.CoordinateConverter;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.followme.FollowMeMission;
import dji.common.mission.followme.FollowMeEvent;
import dji.common.mission.followme.FollowMeExecutionData;
import dji.common.mission.followme.FollowMeHeading;
import dji.common.mission.followme.FollowMeMissionEvent;
import dji.common.mission.followme.FollowMeMissionExecuteState;
import dji.common.mission.followme.FollowMeMissionState;
import dji.common.model.LocationCoordinate2D;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.common.error.DJIError;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.followme.FollowMeMissionOperator;
import dji.sdk.mission.followme.FollowMeMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;
import dji.thirdparty.okhttp3.OkHttpClient;
import dji.thirdparty.okhttp3.Request;
import dji.thirdparty.okhttp3.Response;
import dji.thirdparty.rx.Observable;
import dji.thirdparty.rx.Subscription;
import dji.thirdparty.rx.functions.Action1;
import dji.thirdparty.rx.schedulers.Schedulers;


import com.mrbluyee.djautocontrol.R;


import com.mrbluyee.djautocontrol.application.PhoneLocationApplication;
import com.mrbluyee.djautocontrol.application.DJSDKApplication;
import com.mrbluyee.djautocontrol.application.StationStatusActivity;
import com.mrbluyee.djautocontrol.application.WebRequestApplication;
import com.mrbluyee.djautocontrol.utils.AmapToGpsUtil;
import com.mrbluyee.djautocontrol.utils.ChargeStationInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.mrbluyee.djautocontrol.utils.AmapToGpsUtil.gps_converter;


public class FollowmeActivity extends FragmentActivity implements View.OnClickListener, OnMapClickListener {
    protected static final String TAG = FollowmeActivity.class.getName();

    private MapView mapView;
    private AMap aMap;

    private Button locate, searh_site, gosite,stopmission;

    private double droneLocationLat = 121.40533301729, droneLocationLng = 31.322594332605;
    private double droneStartLat,droneStartLng;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;
    private int target_station_id = 0;

    private float altitude = 10.0f;
    private FlightController mFlightController;
    private FollowMeMissionOperator followMeMissionOperator;
    private LocationCoordinate2D movingObjectLocation;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private Subscription timmerSubcription = null;
    private Observable<Long> timer =Observable.timer(100, TimeUnit.MILLISECONDS).observeOn(Schedulers.computation()).repeat();
    private MyHandler myHandler;

    public SparseArray<ChargeStationInfo> stationInfos = new SparseArray<ChargeStationInfo>();
    private WebRequestApplication webrequest = new WebRequestApplication();

    private int STATION_STATUS_CODE = 1;
    private int SET_STATION_AS_TARGET = 2;

    private double latitude_1cm = 1.141255544679108e-5/100;
    private double longitude_1cm = 8.993216192195822e-6/100;

    private Timer mtimer = null;
    private TimerTask autofreshTask = null;

    private void startTimer(){
        if(mtimer == null){
            mtimer = new Timer();
        }
        if(autofreshTask == null){
            autofreshTask = new TimerTask() {
                @Override
                public void run() {
                    webrequest.Get_chargesite_gps_info(myHandler);
                }
            };
        }
        if((mtimer != null)&&(autofreshTask != null)){
            mtimer.scheduleAtFixedRate(autofreshTask,1000,1000);
        }
    }

    private void stopTimer(){
        if (mtimer != null) {
            mtimer.cancel();
            mtimer = null;
        }
        if(autofreshTask != null){
            autofreshTask.cancel();
            autofreshTask = null;
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        initFlightController();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopTimer();
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(mReceiver);
        removeListener();
        super.onDestroy();
    }

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view){
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string){
        FollowmeActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FollowmeActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {
        locate = (Button) findViewById(R.id.locate);
        searh_site = (Button) findViewById(R.id.followme_search);
        gosite = (Button) findViewById(R.id.followme_gosite);
        stopmission = (Button) findViewById(R.id.followme_stopmission);

        locate.setOnClickListener(this);
        searh_site.setOnClickListener(this);
        gosite.setOnClickListener(this);
        stopmission.setOnClickListener(this);
    }

    private void initMapView() {
        float zoomlevel = (float) 18.0;
        if (aMap == null) {
            aMap = mapView.getMap();
            aMap.setOnMapClickListener(this);// add the listener for click for amap object
        }
        PhoneLocationApplication.initLocation(this);
        LatLng phone_location = gps_converter(new LatLng(PhoneLocationApplication.latitude, PhoneLocationApplication.longitude));
        aMap.addMarker(new MarkerOptions().position(phone_location).title("phone"));
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(phone_location , zoomlevel);
        aMap.moveCamera(cu);
        aMap.setOnMarkerClickListener(markerClickListener); // 绑定 Marker 被点击事件
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_followme);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DJSDKApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        mapView = (MapView) findViewById(R.id.followme_map);
        mapView.onCreate(savedInstanceState);
        initMapView();
        initUI();
        initFlightController();
        addListener();
        myHandler = new MyHandler();
    }

    class MyHandler extends Handler {
        public MyHandler() {
        }

        public MyHandler(Looper L) {
            super(L);
        }
        // 子类必须重写此方法，接受数据
        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            Log.d(TAG,"handleMessage");
            super.handleMessage(msg);
            // 此处可以更新UI
            Bundle b = msg.getData();
            webrequest.chargeStationgpsInfoHandler(b,stationInfos);
            if(stationInfos != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        aMap.clear();
                    }
                });
                markphone();
                for (int i = 0; i < stationInfos.size(); i++) {
                    int station_id = stationInfos.keyAt(i);
                    ChargeStationInfo updatetationInfo = stationInfos.valueAt(i);
                    markchargesite(updatetationInfo.getStationPos(), "" + station_id);
                }
            }
        }
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (followMeMissionOperator != null) {
            followMeMissionOperator.addListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (followMeMissionOperator != null) {
            followMeMissionOperator.removeListener(eventNotificationListener);
        }
    }

    private FollowMeMissionOperatorListener eventNotificationListener = new FollowMeMissionOperatorListener(){
        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(@Nullable DJIError djiError) {
            setResultToToast("Execution finished: " + (djiError == null ? "Success!" : djiError.getDescription()));
        }

        @Override
        public void onExecutionUpdate(@NonNull FollowMeMissionEvent followMeMissionEvent) {

        }
    };

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange()
    {
        initFlightController();
        loginAccount();
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.i(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        setResultToToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightController() {

        BaseProduct product = DJSDKApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }

        if (mFlightController != null) {

            mFlightController.setStateCallback(
                    new FlightControllerState.Callback() {
                        @Override
                        public void onUpdate(FlightControllerState
                                                     djiFlightControllerCurrentState) {
                            droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                            droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                            updateDroneLocation();
                        }
                    });

            followMeMissionOperator = DJISDKManager.getInstance().getMissionControl().getFollowMeMissionOperator();
        }
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){
        LatLng pos = gps_converter(new LatLng(droneLocationLat, droneLocationLng));
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.title("drone");
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = aMap.addMarker(markerOptions);
                }
            }
        });
    }

    private void markchargesite(final LatLng point,String station_id){
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.charge_site));
        markerOptions.title("charge station");
        markerOptions.snippet(station_id);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (checkGpsCoordination(point.latitude, point.longitude)) {
                    Marker marker = aMap.addMarker(markerOptions);
                    mMarkers.put(mMarkers.size(), marker);
                }
            }
        });
    }

    private void markphone(){
        PhoneLocationApplication.initLocation(this);
        final LatLng phone_location = gps_converter(new LatLng(PhoneLocationApplication.latitude, PhoneLocationApplication.longitude));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                aMap.addMarker(new MarkerOptions().position(phone_location).title("phone"));
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                break;
            }
            case R.id.followme_search:{
                startTimer();
                break;
            }
            case R.id.followme_gosite:{
                followMeStart();
                break;
            }
            case R.id.followme_stopmission:{
                followmeStop();
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onMapClick(LatLng point){

    }

    // 定义 Marker 点击事件监听
    AMap.OnMarkerClickListener markerClickListener = new AMap.OnMarkerClickListener() {
        // marker 对象被点击时回调的接口
        // 返回 true 则表示接口已响应事件，否则返回false
        @Override
        public boolean onMarkerClick(Marker marker) {
        if(marker.getTitle().equals("charge station")){
            int id = Integer.parseInt(marker.getSnippet());
            int station_index = stationInfos.indexOfKey(id);
            if (station_index != -1) {
                if (aMap != null) {
                    ChargeStationInfo chargeStationInfo = stationInfos.valueAt(station_index);
                    LatLng station_location = chargeStationInfo.getStationPos();
                    Intent intent = new Intent(FollowmeActivity.this, StationStatusActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putString("stationid",""+id);
                    bundle.putString("longitude",""+station_location.longitude);
                    bundle.putString("latitude",""+station_location.latitude);
                    intent.putExtras(bundle);
                    startActivityForResult(intent, STATION_STATUS_CODE);
                }
            }
        }
        return false;
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode ==  STATION_STATUS_CODE){
            if(resultCode == SET_STATION_AS_TARGET){
                target_station_id = Integer.parseInt(data.getStringExtra("station_id"));
                setResultToToast("Set target addr: charge station "+ target_station_id);
            }
        }
    }

    private void cameraUpdate(){
        LatLng pos = gps_converter(new LatLng(droneLocationLat, droneLocationLng));
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        aMap.moveCamera(cu);
    }

    private void followMeStart(){
        if(followMeMissionOperator != null) {
            if (followMeMissionOperator.getCurrentState().toString().equals(FollowMeMissionState.READY_TO_EXECUTE.toString())) {
                //ToDo: You need init or get the location of your moving object which will be followed by the aircraft.
                if(target_station_id != 0){
                    ChargeStationInfo chargeStationInfo = stationInfos.get(target_station_id);
                    LatLng gps_point = AmapToGpsUtil.toGPSPoint(chargeStationInfo.getStationPos().latitude,chargeStationInfo.getStationPos().longitude);
                    if(checkGpsCoordination(gps_point.latitude,gps_point.longitude)) {
                        movingObjectLocation = new LocationCoordinate2D(gps_point.latitude, gps_point.longitude);
                    }
                }
                if(movingObjectLocation != null) {
                    droneStartLat = droneLocationLat;
                    droneStartLng = droneLocationLng;
                    followMeMissionOperator.startMission(FollowMeMission.getInstance().initUserData(droneStartLat, droneStartLng, altitude), new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            setResultToToast("Mission Start: " + (djiError == null ? "Successfully" : djiError.getDescription()));
                        }
                    });
                    if (!isRunning.get()) {
                        isRunning.set(true);
                        timmerSubcription = timer.subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long aLong) {
                                if(target_station_id != 0){
                                    ChargeStationInfo chargeStationInfo = stationInfos.get(target_station_id);
                                    LatLng gps_point = AmapToGpsUtil.toGPSPoint(chargeStationInfo.getStationPos().latitude,chargeStationInfo.getStationPos().longitude);
                                    if(checkGpsCoordination(gps_point.latitude,gps_point.longitude)) {
                                        movingObjectLocation = new LocationCoordinate2D(gps_point.latitude, gps_point.longitude);
                                    }
                                }
                                if(movingObjectLocation != null) {
                                    double now_latitude_different = (droneStartLat - movingObjectLocation.getLatitude())/latitude_1cm;
                                    double now_longitude_different = (droneStartLng -movingObjectLocation.getLongitude())/longitude_1cm;
                                    if(now_latitude_different < -10){
                                        droneStartLat += latitude_1cm*10;
                                    }else if(now_latitude_different > 10){
                                        droneStartLat -= latitude_1cm*10;
                                    }
                                    if(now_longitude_different < -10){
                                        droneStartLng += longitude_1cm*10;
                                    }else if(now_longitude_different > 10){
                                        droneStartLng -= longitude_1cm*10;
                                    }
                                    followMeMissionOperator.updateFollowingTarget(new LocationCoordinate2D(droneStartLat, droneStartLng),
                                            new CommonCallbacks.CompletionCallback() {
                                                @Override
                                                public void onResult(DJIError error) {
                                                    isRunning.set(false);
                                                }
                                            });
                                }
                            }
                        });
                    }
                }
            } else {
                setResultToToast(followMeMissionOperator.getCurrentState().toString());
            }
        }
    }

    private void followmeStop(){
        if(followMeMissionOperator != null){
            if (followMeMissionOperator.getCurrentState().toString().equals(FollowMeMissionState.EXECUTING.toString())) {
                //ToDo: You need init or get the location of your moving object which will be followed by the aircraft.
                followMeMissionOperator.stopMission(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        setResultToToast("Mission Stop: " + (djiError == null ? "Successfully" : djiError.getDescription()));
                    }
                });
            } else {
                setResultToToast(followMeMissionOperator.getCurrentState().toString());
            }
        }
    }
}
