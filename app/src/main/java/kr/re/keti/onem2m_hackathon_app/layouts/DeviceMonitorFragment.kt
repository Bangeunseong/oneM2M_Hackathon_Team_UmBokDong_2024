package kr.re.keti.onem2m_hackathon_app.layouts

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kr.re.keti.onem2m_hackathon_app.data.AE
import kr.re.keti.onem2m_hackathon_app.databinding.FragmentDeviceMonitorBinding
import kr.re.keti.onem2m_hackathon_app.databinding.ItemRecyclerDeviceBinding
import kr.re.keti.onem2m_hackathon_app.databinding.ItemRecyclerDeviceMonitorBinding

class DeviceMonitorFragment: Fragment() {
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
    inner class DeviceAdapter(private val deviceList: MutableList<AE>) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemRecyclerDeviceMonitorBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(deviceList[position])
        }

        override fun getItemCount(): Int {
            return deviceList.size
        }

        fun addData() {
            notifyItemInserted(viewModel.mutableDeviceList.lastIndex)
        }

        inner class ViewHolder(val binding: ItemRecyclerDeviceMonitorBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(device: AE) {
                binding.deviceName.text = device.applicationName
                binding.deviceStatus.text = "Locked"
            }
        }
    }

    private var _binding: FragmentDeviceMonitorBinding? = null
    private val binding get() = _binding!!

    private var _adapter: DeviceAdapter? = null
    private val adapter get() = _adapter!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _adapter = DeviceAdapter(viewModel.mutableDeviceList)
        viewModel.addedServiceAEName.observe(this) {
            adapter.addData()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentDeviceMonitorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.deviceMonitorRecyclerView.setHasFixedSize(false)
        binding.deviceMonitorRecyclerView.adapter = adapter
        binding.deviceMonitorRecyclerView.layoutManager = GridLayoutManager(context, 2, RecyclerView.VERTICAL, false)
        binding.deviceMonitorRecyclerView.addItemDecoration(ItemPadding(5,5))
    }
}