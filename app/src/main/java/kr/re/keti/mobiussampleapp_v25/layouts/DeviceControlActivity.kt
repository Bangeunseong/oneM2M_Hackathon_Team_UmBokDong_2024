package kr.re.keti.mobiussampleapp_v25.layouts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityDeviceControlBinding

class DeviceControlActivity: AppCompatActivity(), OnMapReadyCallback {
    private var _binding: ActivityDeviceControlBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityDeviceControlBinding.inflate(layoutInflater)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)


        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        TODO("Not yet implemented")
    }
}