package com.tencent.cloud.smh.track

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.beacon.event.open.BeaconReport
import com.tencent.qcloud.core.track.TrackService


/**
 * <p>
 * Created by rickenwang on 2021/8/11.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
abstract class TrackEvent(
    @Transient open val eventCode: String,
) {
    companion object {
        val gson = Gson()
    }

    fun track() {
        if (isIncludeBeacon()) {
            val params = buildParams().toMutableMap()
            params.putAll(commonParams())
            TrackService.getInstance().track(getBeaconKey(), eventCode, params)
        }
    }

    fun trackWithBeaconParams(context: Context) {

        if (isIncludeBeacon()) {
            val params = buildParams().toMutableMap()
            val pubParams = BeaconReport.getInstance().getCommonParams(context)
            params["bundle_id"] = pubParams.boundleId
            params.putAll(commonParams())
            TrackService.getInstance().track(getBeaconKey(), eventCode, params)
        }
    }

    protected abstract fun commonParams(): Map<String, String>

    protected abstract fun getBeaconKey(): String

    private fun buildParams(): Map<String, String> {
        val json = gson.toJson(this)
        return gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
    }
}

private fun isIncludeBeacon(): Boolean {
    return try {
        Class.forName("com.tencent.beacon.event.open.BeaconReport")
        true
    } catch (var1: ClassNotFoundException) {
        false
    }
}