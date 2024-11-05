package kr.re.keti.mobiussampleapp_v25

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kr.re.keti.mobiussampleapp_v25.data_objects.AE
import kr.re.keti.mobiussampleapp_v25.databinding.FragmentDevicesBinding
import kr.re.keti.mobiussampleapp_v25.databinding.ItemRecyclerDeviceBinding
import java.util.ArrayList

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val NET_ADDR_PARAM = "networkAddress"
private const val PAGE_NAME = "기기"

class DeviceFragment : Fragment() {
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
    inner class DeviceAdapter(private val deviceList: ArrayList<AE>) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemRecyclerDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind()
        }

        override fun getItemCount(): Int {
            return deviceList.size
        }

        fun onDataChanged(deviceList: List<AE>){
            this.deviceList.clear()
            deviceList.stream().forEach { this.deviceList.add(it) }
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemRecyclerDeviceBinding): RecyclerView.ViewHolder(binding.root) {
            fun bind(){
                binding.deviceName.text = deviceList[itemCount].applicationName
                binding.deviceStatus.text = "등록됨"
            }
        }
    }

    // Field for this fragment
    private var networkAddress: String? = null
    private var _binding: FragmentDevicesBinding? = null

    private val binding get() = _binding!!

    // onCreate -> Declare important arguments
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            networkAddress = it.getString(NET_ADDR_PARAM)
        }
    }

    // onCreateView -> Declare layout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    // onViewCreated -> Method Declaration
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.deviceText.text = PAGE_NAME
        binding.deviceTabLayout.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener{
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if(tab!!.id == R.id.all_devices){

                } else if(tab.id == R.id.pri_devices){

                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                TODO("Not yet implemented")
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                TODO("Not yet implemented")
            }

        })

        binding.deviceRecyclerView.setHasFixedSize(false)
        binding.deviceRecyclerView.adapter = DeviceAdapter(ArrayList())
        binding.deviceRecyclerView.layoutManager = GridLayoutManager(context, 2)
        binding.deviceRecyclerView.addItemDecoration(ItemPadding(3,3))
    }

    // Need for Fragment Declaration in main activity
    companion object {
        @JvmStatic
        fun newInstance(networkAddress: String) =
            DeviceFragment().apply {
                arguments = Bundle().apply {
                    putString(NET_ADDR_PARAM, networkAddress)
                }
            }
    }
}