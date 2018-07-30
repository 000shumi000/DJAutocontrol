package com.mrbluyee.djautocontrol.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.amap.api.maps2d.model.LatLng;
import com.dji.mapkit.maps.DJIMap;
import com.dji.mapkit.models.DJIBitmapDescriptorFactory;
import com.dji.mapkit.models.DJICameraPosition;
import com.dji.mapkit.models.DJILatLng;
import com.dji.mapkit.models.annotations.DJIMarkerOptions;
import com.mrbluyee.djautocontrol.R;
import com.mrbluyee.djautocontrol.application.DJSDKApplication;
import com.mrbluyee.djautocontrol.application.PhoneLocationApplication;
import com.mrbluyee.djautocontrol.application.WebRequestApplication;
import com.mrbluyee.djautocontrol.utils.AmapToGpsUtil;
import com.mrbluyee.djautocontrol.utils.ChargeStationInfo;
import com.mrbluyee.djautocontrol.utils.DJIdensityUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;
import dji.ux.widget.MapWidget;

import static com.mrbluyee.djautocontrol.utils.AmapToGpsUtil.gps_converter;

public class AutomaticActivity extends Activity {

    private MapWidget mapWidget;
    private ViewGroup parentView;
    private ToggleButton mStartBtn;
    private View fpvWidget;
    private boolean isMapMini = true;
    private DJIMap djiMap = null;
    private int height;
    private int width;
    private int margin;
    private int deviceWidth;
    private int deviceHeight;
    private MyHandler myHandler;
    private float altitude = 100.0f;
    private float mSpeed = 10.0f;
    public SparseArray<ChargeStationInfo> stationInfos = new SparseArray<ChargeStationInfo>();
    private WebRequestApplication webrequest = new WebRequestApplication();
    private Timer mtimer = null;
    private TimerTask autofreshTask = null;
    private List<Waypoint> waypointList = new ArrayList<>();
    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    private double droneLocationLat = 121.40533301729, droneLocationLng = 31.322594332605;
    private ChargeStationInfo selectedChargeStation = null;
    private static final String TAG = AutomaticActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_automatic_camera);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DJSDKApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        height = DJIdensityUtil.dip2px(this, 100);
        width = DJIdensityUtil.dip2px(this, 150);
        margin = DJIdensityUtil.dip2px(this, 12);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        deviceHeight = displayMetrics.heightPixels;
        deviceWidth = displayMetrics.widthPixels;

        mapWidget = (MapWidget) findViewById(R.id.map_widget);
        mapWidget.initAMap(new MapWidget.OnMapReadyListener() {
            @Override
            public void onMapReady(@NonNull DJIMap map) {
                map.setOnMapClickListener(new DJIMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(DJILatLng latLng) {
                        onViewClick(mapWidget);
                    }
                });
            }
        });
        mapWidget.onCreate(savedInstanceState);
        djiMap = mapWidget.getMap();
        initMapView();
        mapWidget.showAllFlyZones();
        parentView = (ViewGroup) findViewById(R.id.root_view);
        fpvWidget = findViewById(R.id.fpv_widget);
        fpvWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onViewClick(fpvWidget);
            }
        });
        mStartBtn = (ToggleButton)findViewById(R.id.auto_start_btn);
        mStartBtn.setOnCheckedChangeListener(startbutton_listener);
        addListener();
        initFlightController();
        myHandler = new MyHandler();
    }

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
            //Log.d(TAG,"handleMessage");
            super.handleMessage(msg);
            // 此处可以更新UI
            Bundle b = msg.getData();
            webrequest.chargeStationgpsInfoHandler(b,stationInfos);
            if(stationInfos != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        djiMap.clear();
                    }
                });
                markphone();
                for (int i = 0; i < stationInfos.size(); i++) {
                    int station_id = stationInfos.keyAt(i);
                    ChargeStationInfo updatetationInfo = stationInfos.valueAt(i);
                    if(station_id == 112130) {
                        selectedChargeStation = stationInfos.get(station_id);
                        //Log.i(TAG, "selectedChargeStation " + selectedChargeStation.getStationId());
                    }
                    DJILatLng station_location = new DJILatLng(updatetationInfo.getStationPos().latitude,updatetationInfo.getStationPos().longitude);
                    markchargesite(station_location, "" + station_id);
                }
            }
        }
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    private void initMapView() {
        float zoomlevel = (float) 18.0;
        if (djiMap != null) {
            PhoneLocationApplication.initLocation(this);
            LatLng phone_location_temp = gps_converter(new LatLng(PhoneLocationApplication.latitude, PhoneLocationApplication.longitude));
            DJILatLng phone_location = new DJILatLng(phone_location_temp.latitude,phone_location_temp.longitude);
            djiMap.addMarker(new DJIMarkerOptions().position(phone_location).title("phone"));
            DJICameraPosition cu = new DJICameraPosition(phone_location , zoomlevel);
            djiMap.setCameraPosition(cu);
        }
    }
    private void markchargesite(final DJILatLng point,String station_id){
        final DJIMarkerOptions markerOptions = new DJIMarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(DJIBitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(),R.drawable.charge_site)));
        markerOptions.title("charge station " + station_id);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (checkGpsCoordination(point.latitude, point.longitude)) {
                    djiMap.addMarker(markerOptions);
                }
            }
        });
    }

    private void markphone(){
        PhoneLocationApplication.initLocation(this);
        LatLng phone_location_temp = gps_converter(new LatLng(PhoneLocationApplication.latitude, PhoneLocationApplication.longitude));
        final DJILatLng phone_location = new DJILatLng(phone_location_temp.latitude,phone_location_temp.longitude);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                djiMap.addMarker(new DJIMarkerOptions().position(phone_location).title("phone"));
            }
        });
    }

    private void onViewClick(View view) {
        if (view == fpvWidget && !isMapMini) {
            resizeFPVWidget(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT, 0, 0);
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, deviceWidth, deviceHeight, width, height, margin);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = true;
            stopTimer();
            mStartBtn.setVisibility(View.INVISIBLE);
            mStartBtn.setEnabled(false);
        } else if (view == mapWidget && isMapMini) {
            resizeFPVWidget(width, height, margin, 3);
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, width, height, deviceWidth, deviceHeight, 0);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = false;
            startTimer();
            mStartBtn.setVisibility(View.VISIBLE);
            mStartBtn.setEnabled(true);
        }
    }

    private CompoundButton.OnCheckedChangeListener startbutton_listener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                mStartBtn.setBackgroundResource(R.drawable.mission_stop);
                configWayPointMission();
                startWaypointMission();
            } else {
                mStartBtn.setBackgroundResource(R.drawable.pointing_start);
                stopWaypointMission();
            }
        }
    };

    private void resizeFPVWidget(int width, int height, int margin, int fpvInsertPosition) {
        RelativeLayout.LayoutParams fpvParams = (RelativeLayout.LayoutParams) fpvWidget.getLayoutParams();
        fpvParams.height = height;
        fpvParams.width = width;
        fpvParams.rightMargin = margin;
        fpvParams.bottomMargin = margin;
        fpvWidget.setLayoutParams(fpvParams);
        parentView.removeView(fpvWidget);
        parentView.addView(fpvWidget, fpvInsertPosition);
    }

    @Override
    protected void onResume() {
        super.onResume();
        initFlightController();
        // Hide both the navigation bar and the status bar.
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        mapWidget.onResume();
    }

    @Override
    protected void onPause() {
        mapWidget.onPause();
        super.onPause();
        stopTimer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopTimer();
    }

    @Override
    protected void onDestroy() {
        mapWidget.onDestroy();
        stopTimer();
        unregisterReceiver(mReceiver);
        removeListener();
        super.onDestroy();
    }

    public void onReturn(View view){
        this.finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapWidget.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapWidget.onLowMemory();
    }

    private class ResizeAnimation extends Animation {

        private View mView;
        private int mToHeight;
        private int mFromHeight;

        private int mToWidth;
        private int mFromWidth;
        private int mMargin;

        private ResizeAnimation(View v, int fromWidth, int fromHeight, int toWidth, int toHeight, int margin) {
            mToHeight = toHeight;
            mToWidth = toWidth;
            mFromHeight = fromHeight;
            mFromWidth = fromWidth;
            mView = v;
            mMargin = margin;
            setDuration(300);
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            float height = (mToHeight - mFromHeight) * interpolatedTime + mFromHeight;
            float width = (mToWidth - mFromWidth) * interpolatedTime + mFromWidth;
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) mView.getLayoutParams();
            p.height = (int) height;
            p.width = (int) width;
            p.rightMargin = mMargin;
            p.bottomMargin = mMargin;
            mView.requestLayout();
        }
    }

    private void setResultToToast(final String string){
        AutomaticActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AutomaticActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

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
                        }
                    });
        }
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {

        }
        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {

        }
        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {

        }
        @Override
        public void onExecutionStart() {

        }
        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            Log.i(TAG, "Execution finished: " + (error == null ? "Success!" : error.getDescription()));
        }
    };

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
        }
        return instance;
    }

    private void configWayPointMission(){
        if(waypointList.size() > 0){
            waypointList.clear();
            waypointMissionBuilder.waypointList(waypointList);
        }
        if(selectedChargeStation != null) {
            LatLng station_location = selectedChargeStation.getStationPos();
            LatLng gps_point = AmapToGpsUtil.toGPSPoint(station_location.latitude, station_location.longitude);
            Waypoint mWaypoint1 = new Waypoint(gps_point.latitude, gps_point.longitude, altitude);
            Waypoint mWaypoint2 = new Waypoint(gps_point.latitude, gps_point.longitude, 20.0f);
            //Add Waypoints to Waypoint arraylist;
            if (waypointMissionBuilder != null) {
                waypointList.add(mWaypoint1);
                waypointList.add(mWaypoint2);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
                waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                        .headingMode(mHeadingMode)
                        .autoFlightSpeed(mSpeed)
                        .maxFlightSpeed(mSpeed)
                        .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
            } else {
                waypointMissionBuilder = new WaypointMission.Builder();
                waypointList.add(mWaypoint1);
                waypointList.add(mWaypoint2);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
                waypointMissionBuilder.finishedAction(mFinishedAction)
                        .headingMode(mHeadingMode)
                        .autoFlightSpeed(mSpeed)
                        .maxFlightSpeed(mSpeed)
                        .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
            }
        }
        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            Log.i(TAG, "loadWaypoint succeeded");
        } else {
            Log.i(TAG, "loadWaypoint failed " + error.getDescription());
        }
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    Log.i(TAG, "Mission upload successfully!");
                } else {
                    Log.i(TAG, "Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });
    }

    private void startWaypointMission(){
        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                Log.i(TAG, "Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
    }

    private void stopWaypointMission(){
        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                Log.i(TAG, "Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
    }
}

