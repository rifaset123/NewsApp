package com.dicoding.newsapp.data.local

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import com.dicoding.newsapp.BuildConfig
import com.dicoding.newsapp.data.local.entity.NewsEntity
import com.dicoding.newsapp.data.local.room.NewsDao
import com.dicoding.newsapp.data.remote.response.NewsResponse
import com.dicoding.newsapp.data.remote.retrofit.ApiService
import com.dicoding.newsapp.utils.AppExecutors
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

// menyimpan data dari network ke local
class NewsRepository private constructor(
    private val apiService: ApiService,
    private val newsDao: NewsDao,
    private val appExecutors: AppExecutors
) {
    // MediatorLiveData digunakan untuk menggabungkan dua sumber data (LiveData) yang berbeda
    private val result = MediatorLiveData<Result<List<NewsEntity>>>()

    // LiveData Builder
    fun getHeadlineNews(): LiveData<Result<List<NewsEntity>>> = liveData{
        emit(Result.Loading) // bukan merupakan LiveData, gunakan fungsi emit
        val client = apiService.getNews(BuildConfig.API_KEY) // Mengambil dari network dengan ApiService

        try {
            val response = apiService.getNews(BuildConfig.API_KEY)
            val articles = response.articles

            val newsList = articles.map { article ->
                val isBookmarked = newsDao.isNewsBookmarked(article.title)
                NewsEntity(
                    article.title,
                    article.publishedAt,
                    article.urlToImage,
                    article.url,
                    isBookmarked
                )
            }
            newsDao.deleteAll()
            newsDao.insertNews(newsList)
        } catch (e: Exception) {
            Log.d("NewsRepository", "getHeadlineNews: ${e.message.toString()} ")
            emit(Result.Error(e.message.toString()))
        }
        val localData: LiveData<Result<List<NewsEntity>>> = newsDao.getNews().map { Result.Success(it) }
        emitSource(localData) // LiveData, gunakan fungsi emitSource.
    }

    // bookmark feature
    fun getBookmarkedNews(): LiveData<List<NewsEntity>> {
        return newsDao.getBookmarkedNews()
    }

    // hanya mengubah sebuah value, yakni isBookmarked dan meng-update-nya pada database
    suspend fun setNewsBookmark(news: NewsEntity, bookmarkState: Boolean) {
        // hapus penggunaan appExecutor karena pakek coroutine
        news.isBookmarked = bookmarkState
        newsDao.updateNews(news)
    }

    companion object {
        @Volatile
        private var instance: NewsRepository? = null
        fun getInstance(
            apiService: ApiService,
            newsDao: NewsDao,
            appExecutors: AppExecutors
        ): NewsRepository =
            instance ?: synchronized(this) {
                instance ?: NewsRepository(apiService, newsDao, appExecutors)
            }.also { instance = it }
    }
}