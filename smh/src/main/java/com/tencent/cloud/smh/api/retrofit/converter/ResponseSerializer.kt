/*
 *
 *  * Copyright (C) 2021 Tencent, Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.tencent.cloud.smh.api.retrofit.converter

/**
 * <p>
 * </p>
 */
//class ResponseSerializer : JsonSerializer<SMHResponse<Any>>, JsonDeserializer<SMHResponse<Any>> {
//    val gson = Gson()
//
//    override fun deserialize(
//        json: JsonElement?,
//        typeOfT: Type?,
//        context: JsonDeserializationContext?
//    ): SMHResponse<Any> {
//        return when {
//            // Is it a JsonObject
//            json?.isJsonObject == true -> {
//
//                // Let's try to extract the type in order
//                // to deserialize this object
//                val parameterizedType = typeOfT as ParameterizedType
//
//                // Returns an Ok with the value deserialized
//                return SMHResponse.Success(
//                    context?.deserialize<Any>(
//                        json,
//                        parameterizedType.actualTypeArguments[0]
//                    )!!
//                )
//            }
//            // Wow, is it an array of objects
//            json?.isJsonArray == true -> {
//
//                // First, let's try to get the array type
//                val parameterizedType = typeOfT as ParameterizedType
//
//                // check if the array contains a generic type too,
//                // for example, List<Result<T, E>>
//                if (parameterizedType.actualTypeArguments[0] is WildcardType) {
//
//                    // In case of yes, let's try to get the type from the
//                    // wildcard type (*)
//                    val internalListType =
//                        (parameterizedType.actualTypeArguments[0] as WildcardType).upperBounds[0] as ParameterizedType
//
//                    // Deserialize the array with the base type Any
//                    // It will give us an array full of linkedTreeMaps (the json)
//                    val arr = context?.deserialize<Any>(
//                        json,
//                        parameterizedType.actualTypeArguments[0]
//                    ) as ArrayList<*>
//
//                    // Iterate the array and
//                    // this time, try to deserialize each member with the discovered
//                    // wildcard type and create new array with these values
//                    val result = arr.map { linkedTreeMap ->
//                        val jsonElement =
//                            gson.toJsonTree(linkedTreeMap as LinkedTreeMap<*, *>).asJsonObject
//                        return@map context.deserialize<Any>(
//                            jsonElement,
//                            internalListType.actualTypeArguments[0]
//                        )
//                    }
//
//                    // Return the result inside the Ok state
//                    return SMHResponse.Success(result)
//                } else {
//                    // Fortunately it is a simple list, like Array<String>
//                    // Just get the type as with a JsonObject and return an Ok
//                    return SMHResponse.Success(
//                        context?.deserialize<Any>(
//                            json,
//                            parameterizedType.actualTypeArguments[0]
//                        )!!
//                    )
//                }
//            }
//            // It is not a JsonObject or JsonArray
//            else -> SMHResponse.Success(null)
//        }
//    }
//
//    override fun serialize(
//        src: SMHResponse<Any>?,
//        typeOfSrc: Type?,
//        context: JsonSerializationContext?
//    ): JsonElement {
//        TODO("Not yet implemented")
//    }
//}