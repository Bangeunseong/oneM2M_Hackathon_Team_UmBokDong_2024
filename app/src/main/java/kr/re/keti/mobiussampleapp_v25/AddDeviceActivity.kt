package kr.re.keti.mobiussampleapp_v25

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityRegisterDeviceBinding

class AddDeviceActivity: AppCompatActivity(), View.OnClickListener {
    private var _binding: ActivityRegisterDeviceBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityRegisterDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setTitle("Add Device")
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.navigationIcon?.mutate().let { icon ->
            icon?.setTint(Color.BLACK)
            binding.toolbar.navigationIcon = icon
        }


    }

    override fun onDestroy() {
        super.onDestroy()

        _binding = null
    }

    override fun onClick(v: View) {

    }
}