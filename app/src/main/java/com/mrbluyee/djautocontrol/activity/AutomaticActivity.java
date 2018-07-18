package com.mrbluyee.djautocontrol.activity;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.RelativeLayout;

import com.amap.api.maps2d.model.LatLng;
import com.dji.mapkit.maps.DJIMap;
import com.dji.mapkit.models.DJIBitmapDescriptorFactory;
import com.dji.mapkit.models.DJICameraPosition;
import com.dji.mapkit.models.DJILatLng;
import com.dji.mapkit.models.annotations.DJIMarkerOptions;
import com.mrbluyee.djautocontrol.R;
import com.mrbluyee.djautocontrol.application.PhoneLocationApplication;
import com.mrbluyee.djautocontrol.application.WebRequestApplication;
import com.mrbluyee.djautocontrol.utils.ChargeStationInfo;
import com.mrbluyee.djautocontrol.utils.DJIdensityUtil;

import java.util.Timer;
import java.util.TimerTask;

import dji.ux.widget.MapWidget;

import static com.mrbluyee.djautocontrol.utils.AmapToGpsUtil.gps_converter;

public class AutomaticActivity extends Activity {

    private MapWidget mapWidget;
    private ViewGroup parentView;
    private View fpvWidget;
    private boolean isMapMini = true;
    private DJIMap djiMap = null;
    private int height;
    private int width;
    private int margin;
    private int deviceWidth;
    private int deviceHeight;
    private MyHandler myHandler;
    public SparseArray<ChargeStationInfo> stationInfos = new SparseArray<ChargeStationInfo>();
    private WebRequestApplication webrequest = new WebRequestApplication();
    private Timer mtimer = null;
    private TimerTask autofreshTask = null;
    private static final String TAG = AutomaticActivity.class.getName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_automatic_camera);
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
                djiMap = mapWidget.getMap();
                initMapView();
                map.setOnMapClickListener(new DJIMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(DJILatLng latLng) {
                        onViewClick(mapWidget);
                    }
                });
            }
        });
        mapWidget.onCreate(savedInstanceState);
        mapWidget.showAllFlyZones();
        parentView = (ViewGroup) findViewById(R.id.root_view);

        fpvWidget = findViewById(R.id.fpv_widget);
        fpvWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onViewClick(fpvWidget);
            }
        });
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
            Log.d(TAG,"handleMessage");
            super.handleMessage(msg);
            // 此处可以更新UI
            Bundle b = msg.getData();
            SparseArray<ChargeStationInfo> stationInfos_temp = webrequest.chargeStationgpsInfoHandler(b);
            if(stationInfos_temp != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        djiMap.clear();
                    }
                });
                markphone();
                for (int i = 0; i < stationInfos_temp.size(); i++) {
                    int station_id = stationInfos_temp.keyAt(i);
                    ChargeStationInfo updatetationInfo = stationInfos_temp.valueAt(i);
                    if (stationInfos.indexOfKey(stationInfos_temp.keyAt(i)) == -1) {    //no data
                        stationInfos.append(station_id, updatetationInfo);
                    } else { //update data
                        stationInfos.put(station_id, updatetationInfo);
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
        } else if (view == mapWidget && isMapMini) {
            resizeFPVWidget(width, height, margin, 3);
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, width, height, deviceWidth, deviceHeight, 0);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = false;
            startTimer();
        }
    }

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
        super.onDestroy();
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
}
