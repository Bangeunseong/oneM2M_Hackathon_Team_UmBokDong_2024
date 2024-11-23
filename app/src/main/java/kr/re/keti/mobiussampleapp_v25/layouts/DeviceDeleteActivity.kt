package kr.re.keti.mobiussampleapp_v25.layouts

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAE
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityDeleteDeviceBinding
import kr.re.keti.mobiussampleapp_v25.databinding.ItemRecyclerDeviceDeleteBinding

class DeviceDeleteActivity: AppCompatActivity() {
    private var _binding: ActivityDeleteDeviceBinding? = null
    private val binding get() = _binding!!
    private var _adapter: DeviceAdapter? = null
    private val adapter get() = _adapter!!
    private val deleteList = mutableListOf<RegisteredAE>()

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityDeleteDeviceBinding.inflate(layoutInflater)
        _adapter = DeviceAdapter(viewModel.getDeviceList())

        binding.deviceRecyclerView.adapter = adapter
        binding.deviceRecyclerView.addItemDecoration(ItemPadding(5,5))
        binding.deviceRecyclerView.layoutManager = GridLayoutManager(this, 2, RecyclerView.VERTICAL, false)
        binding.deviceRecyclerView.setHasFixedSize(false)

        setContentView(binding.root)
    }

    // Inner Class For Decorating Recycler View of Device List
    internal inner class ItemPadding(private val divWidth: Int?, private val divHeight: Int?) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect, view: View,
            parent: RecyclerView, state: RecyclerView.State
        ) {
            super.getItemOffsets(outRect, view, parent, state)
            if (divWidth != null) {
                outRect.left = divWidth
                outRect.right = divWidth
            }
            if(divHeight != null) {
                outRect.top = divHeight
                outRect.bottom = divHeight
            }
        }
    }

    // Inner Class For Setting Adapter in Device Recycler View
    inner class DeviceAdapter(private val deviceList: MutableList<RegisteredAE>) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemRecyclerDeviceDeleteBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(deviceList[position])
        }

        override fun getItemCount(): Int {
            return deviceList.size
        }

        inner class ViewHolder(private val binding: ItemRecyclerDeviceDeleteBinding): RecyclerView.ViewHolder(binding.root) {
            fun bind(device: RegisteredAE){
                binding.deviceName.text = device.applicationName
                binding.deviceStatus.text = if(device.isRegistered) "Registered" else "Unregistered"
                binding.radioButton2.setOnCheckedChangeListener { compoundButton, b ->
                    if(b){
                        deleteList.add(device)
                    }else{
                        deleteList.remove(device)
                    }
                }
            }
        }
    }
}