package com.couple.expensetracker.data.sync

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface DriveApiService {

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id,name,modifiedTime)"
    ): DriveFileList

    @Multipart
    @POST("upload/drive/v3/files?uploadType=multipart")
    suspend fun createFile(
        @Header("Authorization") authHeader: String,
        @Part metadata: MultipartBody.Part,
        @Part content: MultipartBody.Part
    ): DriveFile

    @Multipart
    @PATCH("upload/drive/v3/files/{fileId}?uploadType=multipart")
    suspend fun updateFile(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Part metadata: MultipartBody.Part,
        @Part content: MultipartBody.Part
    ): DriveFile

    @GET("drive/v3/files/{fileId}")
    suspend fun downloadFile(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): Response<ResponseBody>
}

data class DriveFileList(val files: List<DriveFile> = emptyList())
data class DriveFile(val id: String = "", val name: String = "", val modifiedTime: String = "")
