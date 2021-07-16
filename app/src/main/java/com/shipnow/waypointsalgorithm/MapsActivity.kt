package com.shipnow.waypointsalgorithm

import android.content.DialogInterface
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.lifecycle.MutableLiveData

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.DirectionsApi
import com.google.maps.DirectionsApiRequest
import com.google.maps.GeoApiContext
import com.google.maps.PendingResult
import com.google.maps.android.PolyUtil
import com.google.maps.model.DirectionsResult
import com.google.maps.model.GeocodedWaypoint
import com.google.maps.model.TravelMode
import com.shipnow.waypointsalgorithm.com.ManageItemsDialogFragment
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var directionsResult: MutableLiveData<DirectionsResult> = MutableLiveData()
    private var markers:MutableList<MarkerOptions> = mutableListOf()
    private var geocodedWaypoints:MutableLiveData<MutableList<GeocodedWaypoint>> = MutableLiveData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Sydney and move the camera
        val coimbra = LatLng(40.20564, -8.41955)
        mMap.addMarker(MarkerOptions().position(coimbra).title("Marker in Coimbra").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coimbra, 15f))


        getListTenPoints().let {
            it.forEach {
                mMap.addMarker(it)
            }
        }
        //observe for directions
        directionsResult.observe(this, {
            it.routes.forEach {
                val decodePath = PolyUtil.decode(it.overviewPolyline.encodedPath)
                val polyline = PolylineOptions()
                    .addAll(decodePath)
                    .color(Color.GRAY)
                    .width(2f)
                    .jointType(JointType.BEVEL)
                mMap.addPolyline(polyline)
            }
            Log.d("directionsResult", "10 items")
            Log.d("directionsResult", "routes.size = ${it.routes.size}") //resulta apenas 1 rota
            Log.d("directionsResult", "routes.legs.size = ${it.routes[0].legs.size}")
            Log.d("directionsResult", "routes.legs[0].distance = ${it.routes[0].legs[0].distance}")
            Log.d("directionsResult", "routes.legs[0].duration = ${it.routes[0].legs[0].duration}")
            //Log.d("directionsResult", "routes.legs[0].arivalTime = ${it.routes[0].legs[0].arrivalTime.format(DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm:ss Z"))}")
            Log.d("directionsResult", "geocodeWaypoints.size = ${it.geocodedWaypoints.size}") //resulta em 10 waypoints
            Log.d("directionsResult", it.geocodedWaypoints[0].geocoderStatus.name)
            Log.d("directionsResult", it.geocodedWaypoints[0].types[0].name)
            //geocodedWaypoints.postValue(it.geocodedWaypoints.toMutableList())

            //waypoint order, contem a lista de ordens dos stops

            //setup order of the list for optimize route
            syncOptimizeList(it.routes[0].waypointOrder)
        })
    }

    /**
     * organize list sync with optimize output of waypoints
     */
    private fun syncOptimizeList(order: IntArray) {

        val newlist:MutableList<MarkerOptions> = mutableListOf()
        newlist.add(markers.first())
        order.forEachIndexed { index, i ->
            Log.i("arrays", "$index -> $i")
            newlist.add(markers[i+1])
        }
        newlist.add(markers.last())
        markers.clear()
        markers = newlist
    }


    private fun getGeoContext(): GeoApiContext {
        return GeoApiContext.Builder()
            .apiKey(getString(R.string.google_maps_key))

            .build()
    }
    private fun getDirections(listPoints:MutableList<MarkerOptions>){

        val origin = listPoints.first().position
        val destination = listPoints.last().position
//        var waypoints:Array<DirectionsApiRequest.Waypoint> = Array<DirectionsApiRequest.Waypoint>(listPoints.size - 2){
//            DirectionsApiRequest.Waypoint("")
//        }
//        listPoints.drop(1).dropLast(1).forEachIndexed() { i, it ->
//            waypoints[i] = DirectionsApiRequest.Waypoint(it.position.toString())
//        }

        var waypoints:Array<String>
        listPoints.drop(1).dropLast(1).let {
            waypoints = Array<String>(it.size){ "" }
            val a = mutableListOf<String>()
            it.forEach {m ->
                a.add("%s,%s".format(m.position.latitude, m.position.longitude))
            }
            waypoints = a.toTypedArray()
        }

        Log.d("waypoints", "$waypoints")
        waypoints.forEachIndexed { i, it ->
            Log.d("waypoints", "$i  -  $it")
        }


        //get directions from api

        DirectionsApi.newRequest(getGeoContext())
            .mode(TravelMode.DRIVING)
            .origin(com.google.maps.model.LatLng(origin.latitude, origin.longitude))
            .destination(com.google.maps.model.LatLng(destination.latitude, destination.longitude))
            .waypoints(*waypoints)
            .optimizeWaypoints(true)
            .departureTimeNow()
            .setCallback(object : PendingResult.Callback<DirectionsResult>{
                override fun onResult(result: DirectionsResult?) {
                    result?.let{
                        Log.d("DirectionsResult", "$result")
                        directionsResult.postValue(it)
                    }
                }
                override fun onFailure(e: Throwable?) {
                    Log.e("onFailure", "${e?.message}")
                    Log.e("onFailure", "${e?.localizedMessage}")
                    e?.printStackTrace()
                }
            })

    }


    private fun getListTenPoints():MutableList<MarkerOptions>{
        /*
        geomap":{"location":{"lat":40.1965634,"lng":-8.4128111}
        "geomap":{"location":{"lat":40.206452813423,"lng":-8.400919}
        "geomap":{"location":{"lat":40.206452813423,"lng":-8.400919}
        "geomap":{"location":{"lat":40.2034747,"lng":-8.4099254}
        "geomap":{"location":{"lat":40.206452813423,"lng":-8.400919}
        "geomap":{"location":{"lat":40.1932159,"lng":-8.4102545}
        "geomap":{"location":{"lat":40.206452813423,"lng":-8.400919}
        "geomap":{"location":{"lat":40.21097567457,"lng":-8.4023781217041}
        "geomap":{"location":{"lat":40.2722894,"lng":-8.5209973}
        geomap":{"location":{"lat":40.204865863422,"lng":-8.40776955}
         */

        markers = mutableListOf()
        markers.add(MarkerOptions().position(LatLng(40.1965634, -8.4128111)).title("order 1"))
        markers.add(MarkerOptions().position(LatLng(40.206452813423,-8.400919)).title("order 2"))
//        markers.add(MarkerOptions().position(LatLng(40.206452813423,-8.400919)).title("2 copy"))
        markers.add(MarkerOptions().position(LatLng(40.1932138,-8.4101453)).title("order 3"))
        markers.add(MarkerOptions().position(LatLng(40.2034747,-8.4099254)).title("order 4"))
        markers.add(MarkerOptions().position(LatLng(40.204865863422,-8.40776955)).title("order 5"))
        markers.add(MarkerOptions().position(LatLng(40.21097567457,-8.4023781217041)).title("order 6"))
        markers.add(MarkerOptions().position(LatLng(40.1932159,-8.4102545)).title("order 7"))
        markers.add(MarkerOptions().position(LatLng(40.2722894,-8.5209973)).title("order 8"))
        markers.add(MarkerOptions().position(LatLng(40.204865863422,-8.40776955)).title("order 9"))
        markers.add(MarkerOptions().position(LatLng(40.1962633, -8.4299116)).title("order 10"))


        //init btn
        findViewById<ImageButton>(R.id.imageButton).setOnClickListener {
            val d = ManageItemsDialogFragment.newInstance(markers)
            d.setOnDismissListener(DialogInterface.OnDismissListener{
                //reload
                //getDirections(markers)
            })
            d.show(supportFragmentManager, "ManageItems")
        }

        //make request
        getDirections(markers)
        return markers
    }
}