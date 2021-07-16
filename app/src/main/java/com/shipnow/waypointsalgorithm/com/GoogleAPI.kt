package com.shipnow.waypointsalgorithm.com

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import androidx.annotation.Keep
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.common.annotation.KeepName
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.gson.annotations.SerializedName
import com.google.maps.GeoApiContext
import com.google.maps.android.PolyUtil
import com.shipnow.waypointsalgorithm.R
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors


/*
Places:
     import com.google.android.libraries.places.api.Places
     val places = Places.initialize(context, context.resources.getString(R.string.google_maps_key))
 */

interface GoogleAPIProtocol {
    fun updateRequestsCount(count: Int)

    // fun updateResultLog(result: StreetAddress) //method 1
    fun updateResultAddress(addressResult: Address) //method 2

    fun showNetworkError(code: APIError)

    //for map directions
    fun updateResultDirections(mapDirectionsDetails: MapDirectionsUtils)

}

enum class APIError {
    NetworkError, ParserError, UnknownError
}

class GoogleAPI private constructor(private var context: Context) {

    private val TAG = this@GoogleAPI.javaClass.canonicalName
    private val API_BASE_URL =
        "https://maps.googleapis.com/maps/api/geocode/xml?latlng=%s,%s&key=${context.getString(
            R.string.google_maps_key
        )}" //latlng=%s,%s&key=%s
    private val API_DIRECTIONS_URL =
        "https://maps.googleapis.com/maps/api/directions/json?%s&key=${context.getString(
            R.string.google_maps_key
        )}&avoid=tolls"

    private val API_DIRECTIONS_WAYPOINTS_URL =
        "https://maps.googleapis.com/maps/api/directions/json?%s&key=${context.getString(
            R.string.google_maps_key
        )}&avoid=tolls"

    //width x heigth  //path=38.742041,-9.168739|39.752466,-8.794299|40.210903,-8.384689|40.198890,-8.409989 //markers=size:mid|color:red|40.198890,-8.409989&markers=size:mid|color:red|40.198890,-8.409989
    private val API_STATIC_IMAGE_URL =
        "https://maps.googleapis.com/maps/api/staticmap?&size=%sx%s&%s&maptype=roadmap%s&key=${context.resources.getString(
            R.string.google_maps_key
        )}&format=png"

    private lateinit var client: OkHttpClient
    private var geocoder: Geocoder? = null
    private var places = null

    private val lock = Object()

    var delegate: GoogleAPIProtocol? = null

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var singleton: GoogleAPI? = null

        fun singleton(): GoogleAPI {
            return if (singleton == null) {
                throw IllegalStateException("Must Initialize GoogleAPI before using singleton()")
            } else {
                singleton!!
            }
        }

        // call this method once, on app.create
        // we need the context to access request template from the strings
        fun with(context: Context): GoogleAPI {
            if (singleton == null) {
                if (singleton == null) {
                    singleton = GoogleAPI(context)
                    singleton?.init()
                }
            }
            return singleton!!
        }
    }

    private fun init() {
        //method 1
        //client = OkHttpClient.Builder().build()
        geocoder = Geocoder(context)
    }

    fun addRequest(request: LatLng) {
        synchronized(lock) {
            jobExecutor.execute(JobRunnable(request))
            delegate?.updateRequestsCount(jobExecutor.getQueueSize())
        }
    }


    /**
     * method : getStaticMapImageURL , return URL image for maps route
     * @param overviewPolyline can be the response field route[0].overview_polyline.points of getDirections(...)
     */
    fun getStaticMapImageURL(
        points: MutableList<LatLng>,
        width: Int,
        height: Int,
        overviewPolyline: String? = null
    ): String {

        val encodingOverviewrouteEncoding = PolyUtil.encode(points)

        Log.i(TAG, "getStaticMapImageURL encodedpolyline \n$encodingOverviewrouteEncoding")

        val marker = "&markers=scale:2|icon:%s|%s,%s"   //"&markers=color:%s|%s,%s"
        var markers: String = ""
        ///markers=size:mid|color:red|40.198890,-8.409989&markers=size:mid|color:red|40.198890,-8.409989
        var color = "red"
        for (i in 0 until points.size) {
            color = when {
                i == 0 -> {
                    "http://you-ship.com/assets/map/pick.png" //blue
                }
                i < (points.size - 1) -> {
                    "http://you-ship.com/assets/map/stop.png" //orange
                }
                else -> {
                    "http://you-ship.com/assets/map/drop.png" //red
                }
            }
            markers += marker.format(
                color,
                points[i].latitude,
                points[i].longitude
            )
        }


        Log.i(TAG, "link to maps directions --  start")
        Log.i(
            TAG, API_STATIC_IMAGE_URL.format(
                width, height,
                overviewPolyline,
                markers
            )
        )
        Log.i(TAG, "link to maps directions -- end")


        Log.i(TAG, "getStaticMapImageURL markers \n$markers")
        overviewPolyline?.let {
            return API_STATIC_IMAGE_URL.format(
                width, height,
                it,
                markers
            )
        }



        return API_STATIC_IMAGE_URL.format(
            width, height,
            encodingOverviewrouteEncoding,
            markers
        )
    }

    fun getDirectionsURL(origin: LatLng, dest: LatLng): String {
        val str2points = "origin=%s,%s&destination=%s,%s"
        val url = API_DIRECTIONS_URL.format(
            str2points.format(
                origin.latitude,
                origin.longitude,
                dest.latitude,
                dest.longitude
            )
        )
        return url
    }

    /**
     * method : getDirections , for maps route
     *
     */
    fun getDirections(origin: LatLng, dest: LatLng) {

        val str2points = "origin=%s,%s&destination=%s,%s"
        val url = API_DIRECTIONS_URL.format(
            str2points.format(
                origin.latitude,
                origin.longitude,
                dest.latitude,
                dest.longitude
            )
        )

        return getDirections(url)
    }

    fun getDirections(points: MutableList<LatLng>) {

        val str2poins = "&origin=%s,%s&destination=%s,%s&"
        var strAllPoints: String = ""
        for (i in 0 until (points.size - 1)) {

            strAllPoints += str2poins.format(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )

        }
        val url = API_DIRECTIONS_URL.format(
            strAllPoints
        )
        return getDirections(url)
    }

    @Keep
    @KeepName
    private fun getDirections(apiUrlWithDirections: String) {

        var bounds: LatLngBounds? = null
        var distance: MapDirectionsUtils.Distance? = null
        var duration: MapDirectionsUtils.Duration? = null
        val path: MutableList<List<LatLng>> = mutableListOf()
        val directionsRequest = object : StringRequest(
            Method.GET,
            apiUrlWithDirections,
            com.android.volley.Response.Listener<String> { response ->
                val jsonResponse = org.json.JSONObject(response)
                //Log.d(TAG, "response: $response")

                // Get routes
                val routes = jsonResponse.getJSONArray("routes")
                if (routes.length() == 0) return@Listener
                val overviewPolyline =
                    routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                val getbounds = routes.getJSONObject(0).getJSONObject("bounds")
                val legs = routes.getJSONObject(0).getJSONArray("legs")
                val steps = legs.getJSONObject(0).getJSONArray("steps")
                distance = MapDirectionsUtils.Distance(
                    legs.getJSONObject(0).getJSONObject("distance").getString("text"),
                    legs.getJSONObject(0).getJSONObject("distance").getDouble("value")
                )
                duration = MapDirectionsUtils.Duration(
                    legs.getJSONObject(0).getJSONObject("duration").getString("text"),
                    legs.getJSONObject(0).getJSONObject("duration").getDouble("value")
                )

                /*
                get bounds latLng
                "routes" : [
                       {
                          "bounds" : {
                             "northeast" : {
                                "lat" : 40.20518209999999,
                                "lng" : -8.402589299999999
                             },
                             "southwest" : {
                                "lat" : 40.1918684,
                                "lng" : -8.4138983
                             }
                          },
                 */
                val northeast = getbounds.getJSONObject("northeast")
                val southwest = getbounds.getJSONObject("southwest")
                bounds = LatLngBounds(
                    LatLng(southwest.getDouble("lat"), southwest.getDouble("lng")),
                    LatLng(northeast.getDouble("lat"), northeast.getDouble("lng"))
                )

                for (i in 0 until steps.length()) {
                    val points =
                        steps.getJSONObject(i).getJSONObject("polyline").getString("points")
                    path.add(PolyUtil.decode(points))
                }

                //was complete with sucess return with all points
                // usage:
                //  for (i in 0 until path.size) {
                //      this.googleMap!!.addPolyline(PolylineOptions().addAll(path[i]).color(Color.RED))
                //  }

                //finally
                val mapDirectionDetails = MapDirectionsUtils()
                mapDirectionDetails.list = path
                mapDirectionDetails.bounds = bounds
                mapDirectionDetails.distance = distance
                mapDirectionDetails.duration = duration
                mapDirectionDetails.overviewPolyline = overviewPolyline
                delegate?.updateResultDirections(mapDirectionDetails)
            },
           com.android.volley.Response.ErrorListener { _ ->
                delegate?.showNetworkError(APIError.NetworkError)
            }) {}
        val requestQueue = Volley.newRequestQueue(context)
        requestQueue.add(directionsRequest)
        //return MapDirectionsUtils(path, bounds, distance, duration)
    }


    //get GeoContext //'com.google.maps:google-maps-services:0.2.11'
    fun getGeoContext(): GeoApiContext {
        return GeoApiContext.Builder()
            .apiKey(context.resources.getString(R.string.google_maps_key)).build()
    }


    /**
     * method 1 : get from API_BASE_URL
     *
     * init() -> client = OkHttpClient.Builder().build()
     */
/*
private fun doReverseGeocode(location: LatLng) {
    delegate?.updateRequestsCount(jobExecutor.getQueueSize())

    val url = API_BASE_URL.format(location.latitude, location.longitude)

    val request = Request.Builder()
        .url(url)
        .addHeader("Content-Type", "text/xml")
        .build()

    try {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                delegate?.showNetworkError(APIError.NetworkError)
            }

            override fun onResponse(call: Call, response: Response) {
                val strResult = response.body?.string()
                try {
                    Log.i(TAG, "result: " + strResult)
                    val res = xmlParser.parse(strResult!!)
                    val objectAddress = StreetAddress(res)
                    //uncomment line below if will be to use this method
                   // delegate?.updateResultLog(objectAddress)
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                    delegate?.showNetworkError(APIError.ParserError)
                }
            }

        })
    } catch (e: Exception) {
        delegate?.showNetworkError(APIError.UnknownError)
    }

}
*/

    /**
     * method 2 : get from Geocoder
     * https://developer.android.com/reference/android/location/Geocoder.html
     * should init on activity -> fun with(context: Context): GoogleAPI
     */

    private fun doReverseGeocodeFromGeocoder(location: LatLng) {

        delegate?.updateRequestsCount(jobExecutor.getQueueSize())
        try {
            val addressResult =
                geocoder?.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                )
            if (addressResult != null)
                delegate?.updateResultAddress(addressResult[0])
        } catch (e: Exception) {
            delegate?.showNetworkError(APIError.ParserError)
        }
    }


    internal inner class JobRunnable(val location: LatLng) : Runnable {
        override fun run() {

            //method 1
            // doReverseGeocode(location)
            //method 2
            doReverseGeocodeFromGeocoder(location)
            // need to delay a request to the server
            // otherwise, the changes of the Number Outstanding Requests field
            // may not be visible by eye (Server responds very fast)
            Thread.sleep(500)
        }
    }

    private val jobExecutor = SerialExecutor(Executors.newSingleThreadExecutor())

    internal inner class SerialExecutor(private val executor: Executor) : Executor {
        private val tasks: Queue<Runnable> = ArrayDeque()
        private var active: Runnable? = null

        @Synchronized
        fun getQueueSize(): Int {
            return tasks.size
        }

        @Synchronized
        override fun execute(r: Runnable) {
            tasks.offer(Runnable {
                try {
                    r.run()
                } finally {
                    scheduleNext()
                }
            })
            if (active == null) {
                scheduleNext()
            }
        }

        @Synchronized
        private fun scheduleNext() {
            active = tasks.poll()
            active?.let {
                executor.execute(it)
            }
        }
    }
}


class MapDirectionsUtils {
    @SerializedName("list")
    var list: MutableList<List<LatLng>> = mutableListOf()

    @SerializedName("bounds")
    var bounds: LatLngBounds? = null

    @SerializedName("distance")
    var distance: Distance? = null

    @SerializedName("duration")
    var duration: Duration? = null

    @SerializedName("overviewPolyline")
    var overviewPolyline: String? = null

    @SerializedName("route")
    var route: MutableList<JSONObject>? = null

    data class Duration(
        @SerializedName("text") val text: String = "",
        @SerializedName("value") val value: Double    //seconds
    )

    data class Distance(
        @SerializedName("text") val text: String = "",
        @SerializedName("value") val value: Double    //meters
    )

    override fun toString(): String {
        return "MapDirectionsUtils(bounds=$bounds, distance=$distance, duration=$duration)"
    }


}
