package com.example.tweakly.di

import android.content.Context
import androidx.room.Room
import com.example.tweakly.data.local.TweaklyDatabase
import com.example.tweakly.data.local.dao.PhotoDao
import com.example.tweakly.data.remote.AuthInterceptor
import com.example.tweakly.data.remote.TweaklyApi
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val BASE_URL = "https://tweaklybot-server.onrender.com/"

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideTweaklyApi(retrofit: Retrofit): TweaklyApi =
        retrofit.create(TweaklyApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TweaklyDatabase =
        Room.databaseBuilder(context, TweaklyDatabase::class.java, "tweakly_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun providePhotoDao(db: TweaklyDatabase): PhotoDao = db.photoDao()
}
