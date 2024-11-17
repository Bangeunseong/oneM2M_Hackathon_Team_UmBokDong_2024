package kr.re.keti.mobiussampleapp_v25.layouts

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import kr.re.keti.mobiussampleapp_v25.R
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityAddDeviceBinding
import kr.re.keti.mobiussampleapp_v25.utils.DeviceAddListener

class DeviceAddActivity: AppCompatActivity(), DeviceAddListener {
    private var _binding: ActivityAddDeviceBinding? = null
    private val binding get() = _binding!!
    private var _deviceRegisterDialog: DeviceRegisterDialog? = null
    private val deviceRegisterDialog get() = _deviceRegisterDialog!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityAddDeviceBinding.inflate(layoutInflater)
        _deviceRegisterDialog = DeviceRegisterDialog(this)
        setContentView(binding.root)

        binding.toolbar.setTitle("Add Device")
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.navigationIcon?.mutate().let { icon ->
            icon?.setTint(Color.BLACK)
            binding.toolbar.navigationIcon = icon
        }

        binding.addDevice.setOnClickListener {
            deviceRegisterDialog.show(supportFragmentManager, "DeviceAddActivity")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        _binding = null
        _deviceRegisterDialog = null
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_add_option, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            android.R.id.home -> {
                setResult(RESULT_CANCELED)
                finish()
                true
            }
            R.id.menu_show_support_device -> {
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun setDeviceName(serviceAE: String?) {
        Log.d("DeviceAddActivity", "Data: $serviceAE")
        intent.putExtra("SERVICE_AE", serviceAE)
        setResult(RESULT_OK, intent)
        finish()
    }
}