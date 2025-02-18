package com.tencent.cloud.smh.track

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.beacon.event.open.BeaconEvent
import com.tencent.beacon.event.open.BeaconReport
import com.tencent.beacon.event.open.EventType

/**
 * <p>
 * Created by rickenwang on 2021/8/11.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
abstract class TrackEvent(
    @Transient open val eventCode: String
) {
    companion object {
        val gson = Gson()
    }

    fun track() {
        if (isIncludeBeacon()) {
            val params = buildParams().toMutableMap()
            params.putAll(commonParams())
            directReport(getBeaconKey(), eventCode, params)
        }
    }

    fun trackWithBeaconParams(context: Context) {
        if (isIncludeBeacon()) {
            val params = buildParams().toMutableMap()
            val pubParams = BeaconReport.getInstance().getCommonParams(context)
            params["bundle_id"] = pubParams.boundleId
            params.putAll(commonParams())
            directReport(getBeaconKey(), eventCode, params)
        }
    }

    protected abstract fun commonParams(): Map<String, String>

    protected abstract fun getBeaconKey(): String

    private fun buildParams(): Map<String, String> {
        val json = gson.toJson(this)
        return gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
    }
}

// TODO: 2022/5/11 开放foundation中isIncludeBeacon
private fun isIncludeBeacon(): Boolean {
    return try {
        Class.forName("com.tencent.beacon.event.open.BeaconReport")
        Class.forName("com.tencent.qimei.sdk.QimeiSDK")
        true
    } catch (var1: ClassNotFoundException) {
        false
    }
}

private fun directReport(beaconKey: String, eventCode: String, params: Map<String, String>) {
    val builder = BeaconEvent.builder()
        .withAppKey(beaconKey)
        .withCode(eventCode)
        .withType(EventType.NORMAL)
        .withParams(params)
    val result = BeaconReport.getInstance().report(builder.build())
//    if (QCloudTrackService.getInstance().setDebug()) {
//        val mapAsString = StringBuilder("{")
//        for (key in params.keys) {
//            mapAsString.append(key + "=" + params[key] + ", ")
//        }
//        mapAsString.delete(mapAsString.length - 2, mapAsString.length).append("}")
//        Log.i(
//            "TrackEvent", String.format(
//                "beaconKey: %s, eventCode: %s, params: %s => result{ eventID: %s, errorCode: %d, errorMsg: %s}",
//                beaconKey, eventCode, mapAsString, result.eventID, result.errorCode, result.errMsg
//            )
//        )
//    }
}