package com.whatsapp.logger.data.api

import com.whatsapp.logger.data.api.model.*
import retrofit2.http.*

interface ApiService {

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

    /** The server's in-memory console log (same data as the web app's /logs page). */
    @GET("/logs")
    fun getServerLogs(
        @Query("format") format: String,
        @Header("Authorization") authHeader: String
    ): retrofit2.Call<List<ServerLogDto>>

    @GET("/api/health")
    fun getHealth(
        @Header("Authorization") authHeader: String
    ): retrofit2.Call<HealthDto>

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