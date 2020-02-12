package eu.kanade.tachiyomi.data.track.myanimelist

import android.content.Context
import android.graphics.Color
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class Myanimelist(private val context: Context, id: Int) : TrackService(id) {

    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 6

        const val DEFAULT_STATUS = READING
        const val DEFAULT_SCORE = 0

        const val BASE_URL = "https://myanimelist.net"
        const val USER_SESSION_COOKIE = "MALSESSIONID"
        const val LOGGED_IN_COOKIE = "is_logged_in"
    }

    private val interceptor by lazy { MyAnimeListInterceptor(this) }
    private val api by lazy { MyAnimelistApi(client, interceptor) }

    override val name: String = "MyAnimeList"

    override fun getLogo() = R.drawable.ic_tracker_mal_logo

    override fun getLogoColor() = Color.rgb(46, 81, 162)

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            READING -> getString(R.string.reading)
            COMPLETED -> getString(R.string.completed)
            ON_HOLD -> getString(R.string.on_hold)
            DROPPED -> getString(R.string.dropped)
            PLAN_TO_READ -> getString(R.string.plan_to_read)
            else -> ""
        }
    }

    override fun getStatusList(): List<Int> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)
    }

    override fun getScoreList(): List<String> {
        return IntRange(0, 10).map(Int::toString)
    }

    override fun displayScore(track: Track): String {
        return track.score.toInt().toString()
    }

    override suspend fun add(track: Track): Track {
        return api.addLibManga(track)
    }

    override suspend fun update(track: Track): Track {
        if (track.total_chapters != 0 && track.last_chapter_read == track.total_chapters) {
            track.status = COMPLETED
        }

        return api.updateLibManga(track)
    }

    override suspend fun bind(track: Track): Track {
        val remoteTrack = api.findLibManga(track)
        if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.score = DEFAULT_SCORE.toFloat()
            track.status = DEFAULT_STATUS
            add(track)
        }
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String): Boolean {
        logout()
        try {
            val csrf = api.login(username, password)
            saveCSRF(csrf)
            saveCredentials(username, password)
            return true

        } catch (e: Exception) {
            logout()
            return false
        }
    }

    fun refreshLogin() {
        val username = getUsername()
        val password = getPassword()
        logout()

        try {
            val csrf = api.login(username, password)
            saveCSRF(csrf)
            saveCredentials(username, password)
        } catch (e: Exception) {
            logout()
            throw e
        }
    }

    // Attempt to login again if cookies have been cleared but credentials are still filled
    fun ensureLoggedIn() {
        if (isAuthorized) return
        if (!isLogged) throw Exception("MAL Login Credentials not found")

        refreshLogin()
    }

    override fun logout() {
        super.logout()
        preferences.trackToken(this).delete()
        networkService.cookieManager.remove(BASE_URL.toHttpUrlOrNull()!!)
    }

    val isAuthorized: Boolean
        get() = super.isLogged &&
                getCSRF().isNotEmpty() &&
                checkCookies()

    fun getCSRF(): String = preferences.trackToken(this).getOrDefault()

    private fun saveCSRF(csrf: String) = preferences.trackToken(this).set(csrf)

    private fun checkCookies(): Boolean {
        var ckCount = 0
        val url = BASE_URL.toHttpUrlOrNull()!!
        for (ck in networkService.cookieManager.get(url)) {
            if (ck.name == USER_SESSION_COOKIE || ck.name == LOGGED_IN_COOKIE)
                ckCount++
        }

        return ckCount == 2
    }

}
