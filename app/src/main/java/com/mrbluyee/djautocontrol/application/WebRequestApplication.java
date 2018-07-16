package com.mrbluyee.djautocontrol.application;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;

import com.amap.api.maps2d.model.LatLng;
import com.mrbluyee.djautocontrol.utils.ChargeStationInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import dji.thirdparty.okhttp3.HttpUrl;
import dji.thirdparty.okhttp3.MediaType;
import dji.thirdparty.okhttp3.OkHttpClient;
import dji.thirdparty.okhttp3.Request;
import dji.thirdparty.okhttp3.RequestBody;
import dji.thirdparty.okhttp3.Response;

public class WebRequestApplication {
    public void Get_chargesite_gps_info(final Handler UIHandler){
        new Thread(new Runnable() {
            String  url = "http://139.196.138.204/tp5/public/station/returngaodegps";
            String result = null;
            @Override
            public void run() {
                try {
                    OkHttpClient client = new OkHttpClient();//创建OkHttpClient对象
                    Request request = new Request.Builder()
                        .url(url)//请求接口
                        .get()
                        .build();//创建Request 对象
                    Response response = null;
                    response = client.newCall(request).execute();//得到Response 对象
                    if (response.isSuccessful()) {
                        //Log.d("getgps","response.code()=="+response.code());
                        result = response.body().string();
                        Message msg = new Message();
                        Bundle b = new Bundle();// 存放数据
                        b.putString("getgps",result);
                        msg.setData(b);
                        UIHandler.sendMessage(msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public SparseArray<ChargeStationInfo> chargeStationgpsInfoHandler(Bundle b)
    {
        SparseArray<ChargeStationInfo> stationInfos = null;
        JSONArray station_gps_jsonArray = null;
        String station_gps_data = b.getString("getgps");
        if(station_gps_data != null) {
            //Log.d("MyHandler", station_gps_data);
            try {
                stationInfos = new SparseArray<ChargeStationInfo>();
                station_gps_jsonArray = new JSONArray(station_gps_data);
                for (int i = 0; i < station_gps_jsonArray.length(); i++) {
                    JSONObject jsonObject = station_gps_jsonArray.getJSONObject(i);
                    int station_id = jsonObject.getInt("stationid");
                    double station_lat = jsonObject.getDouble("lat");
                    double station_lon = jsonObject.getDouble("lon");
                    LatLng station_pos = new LatLng(station_lat, station_lon);
                    String station_create_time = jsonObject.getString("create_time");
                    String station_update_time = jsonObject.getString("update_time");
                    ChargeStationInfo stationInfo = new ChargeStationInfo();
                    stationInfo.setStationPos(station_pos);
                    stationInfo.setStation_create_time(station_create_time);
                    stationInfo.setStation_update_time(station_update_time);
                    stationInfos.append(station_id, stationInfo);
                    //Log.d("MyHandler", "append station:"+ station_id);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return stationInfos;
    }

    public void Get_chargesite_info(final Handler UIHandler,final String stationid){
        new Thread(new Runnable() {
            String  url = "http://139.196.138.204/tp5/public/station/readstation";
            String result = null;
            @Override
            public void run() {
                try {
                    HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
                    urlBuilder.addQueryParameter("stationid",stationid);
                    OkHttpClient client = new OkHttpClient();//创建OkHttpClient对象
                    Request request = new Request.Builder()
                        .url(urlBuilder.build())//请求接口。
                        .get()
                        .build();//创建Request 对象
                    Response response = null;
                    response = client.newCall(request).execute();//得到Response 对象
                    if (response.isSuccessful()) {
                        //Log.d("getinfo","response.code()=="+response.code());
                        result = response.body().string();
                        Message msg = new Message();
                        Bundle b = new Bundle();// 存放数据
                        b.putString("getinfo",result);
                        msg.setData(b);
                        UIHandler.sendMessage(msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public ChargeStationInfo chargeSiteInfoHandler(Bundle b)
    {
        ChargeStationInfo stationInfo = new ChargeStationInfo();
        JSONObject station_info_json = null;
        String station_info_data = b.getString("getinfo");
        if(station_info_data != null) {
            //Log.d("MyHandler", station_info_data);
            try {
                station_info_json = new JSONObject(station_info_data);
                String station_id = station_info_json.getString("stationid");
                String uavid = station_info_json.getString("uavid");
                String uav_pow = station_info_json.getString("power");
                String station_create_time = station_info_json.getString("create_time");
                String station_update_time = station_info_json.getString("update_time");
                String station_status = station_info_json.getString("stationstatus");
                String station_control = station_info_json.getString("stationcontrol");
                stationInfo.setStationId(station_id);
                stationInfo.setDroneId(uavid);
                stationInfo.setDronePow(uav_pow);
                stationInfo.setStation_create_time(station_create_time);
                stationInfo.setStation_update_time(station_update_time);
                stationInfo.setStationStatus(station_status);
                stationInfo.setStationcontrol(station_control);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return stationInfo;
    }

    public void Post_drone_info(final String droneinfo) {
        new Thread(new Runnable() {
            String url = "http://139.196.138.204/tp5/public/station/updateuav";
            String result = null;
            @Override
            public void run() {
                try {
                    OkHttpClient client = new OkHttpClient();//创建OkHttpClient对象
                    MediaType table = MediaType.parse("application/x-www-form-urlencoded");
                    RequestBody body = RequestBody.create(table,droneinfo);
                    Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();
                    Response response = null;
                    response = client.newCall(request).execute();//得到Response 对象
                    if (response.isSuccessful()) {
                        //Log.d("postdroneinfo", "response.code()==" + response.code());
                        result = response.body().string();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
