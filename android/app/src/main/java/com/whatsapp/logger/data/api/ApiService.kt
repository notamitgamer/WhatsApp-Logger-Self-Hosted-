package com.whatsapp.logger.data.api

import com.whatsapp.logger.data.api.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("/api/chats/stream")
    fun getChatsStream(
        @Query("token") token: String
    ): Response<ResponseBody>

    @GET("/api/sync/stream")
    fun getSyncStream(
        @Query("token") token: String,
        @Query("since") since: Long
    ): Response<ResponseBody>

    @GET("/api/export/chats")
    fun getExportChats(
        @Query("token") token: String
    ): retrofit2.Call<ExportChatsResponse>

    @GET("/api/export/messages")
    fun getExportMessages(
        @Query("token") token: String,
        @Query("chatId") chatId: String
    ): retrofit2.Call<ExportMessagesResponse>

    @POST("/api/rename")
    fun renameChat(
        @Header("Authorization") authHeader: String,
        @Body request: RenameRequest
    ): retrofit2.Call<RenameResponse>

    @GET("/api/export")
    fun getFullExport(
        @Query("token") token: String
    ): retrofit2.Call<FullExportResponse>
}

interface AuthApiService {
    @POST("/api/verify")
    fun verifyCredentials(
        @Body request: VerifyRequest
    ): retrofit2.Call<VerifyResponse>
}