package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.newCallWithProgress
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.utils.MdUtil
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest

/**
 * A simple implementation for sources from a website.
 */
abstract class HttpSource : Source {

    /**
     * Network service.
     */
    protected val network: NetworkHelper by injectLazy()

//    /**
//     * Preferences that a source may need.
//     */
//    val preferences: SharedPreferences by lazy {
//        Injekt.get<Application>().getSharedPreferences("source_$id", Context.MODE_PRIVATE)
//    }

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    val baseUrl = MdUtil.baseUrl

    override val name = "Mangadex"

    /**
     * Version id used to generate the source id. If the site completely changes and urls are
     * incompatible, you may increase this value and it'll be considered as a new source.
     */
    open val versionId = 1

    /**
     * Id of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string: sourcename/language/versionId
     * Note the generated id sets the sign bit to 0.
     */
    override val id by lazy {
        val key = "${name.toLowerCase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    /**
     * Headers used for requests.
     */
    val headers: Headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    val client: OkHttpClient = network.cloudflareClient.newBuilder().build()

    /**
     * Headers builder for request.
     */
    protected fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Neko/MangaDex Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.131 Safari/537.36")
        add("X-Requested-With", "XMLHttpRequest")
    }


    /**
     * Visible name of the source.
     */
    override fun toString() = "$name (${lang.toUpperCase()})"


    /**
     * Returns an observable with the page list for a chapter.
     *
     * @param chapter the chapter whose page list has to be fetched.
     */
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
                .asObservableSuccess()
                .map { response ->
                    pageListParse(response)
                }
    }

    open fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    /**
     * Returns the request for getting the page list. Override only if it's needed to override the
     * url, send different headers or request method like POST.
     *
     * @param chapter the chapter whose page list has to be fetched.
     */
    open protected fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    /**
     * Parses the response from the site and returns a list of pages.
     *
     * @param response the response from the site.
     */
    abstract protected fun pageListParse(response: Response): List<Page>

    /**
     * Returns an observable with the page containing the source url of the image. If there's any
     * error, it will return null instead of throwing an exception.
     *
     * @param page the page whose source image has to be fetched.
     */
    open fun fetchImageUrl(page: Page): Observable<String> {
        return client.newCall(imageUrlRequest(page))
                .asObservableSuccess()
                .map { imageUrlParse(it) }
    }

    /**
     * Returns the request for getting the url to the source image. Override only if it's needed to
     * override the url, send different headers or request method like POST.
     *
     * @param page the chapter whose page list has to be fetched
     */
    open protected fun imageUrlRequest(page: Page): Request {
        return GET(page.url, headers)
    }

    /**
     * Parses the response from the site and returns the absolute url to the source image.
     *
     * @param response the response from the site.
     */
    abstract protected fun imageUrlParse(response: Response): String

    /**
     * Returns an observable with the response of the source image.
     *
     * @param page the page whose source image has to be downloaded.
     */
    fun fetchImage(page: Page): Observable<Response> {
        return client.newCallWithProgress(imageRequest(page), page)
                .asObservableSuccess()
    }

    /**
     * Returns the request for getting the source image. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param page the chapter whose page list has to be fetched
     */
    open protected fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }


    /**
     * Called before inserting a new chapter into database. Use it if you need to override chapter
     * fields, like the title or the chapter number. Do not change anything to [manga].
     *
     * @param chapter the chapter to be added.
     * @param manga the manga of the chapter.
     */
    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {
    }

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = FilterList()
}
