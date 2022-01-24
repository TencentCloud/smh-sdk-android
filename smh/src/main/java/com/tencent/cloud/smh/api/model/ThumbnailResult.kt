package com.tencent.cloud.smh.api.model

import com.tencent.cloud.smh.LOCATION_KEY

/**
 * <p>
 * Created by rickenwang on 2021/6/1.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
data class ThumbnailResult(val location: String?) {

    constructor(
        headers: Map<String, List<String>>
    ) : this(
        headers.getOrElse(LOCATION_KEY, { null })?.first(),
    )

}