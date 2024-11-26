package kr.re.keti.mobiussampleapp_v25.layouts

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.re.keti.mobiussampleapp_v25.R
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAE
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAEDatabase
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityDeleteDeviceBinding
import kr.re.keti.mobiussampleapp_v25.databinding.ItemRecyclerDeviceDeleteBinding

class DeviceDeleteActivity: AppCompatActivity() {
    private var _binding: ActivityDeleteDeviceBinding? = null
    private val binding get() = _binding!!
    private var _adapter: DeviceAdapter? = null
    private val adapter get() = _adapter!!
    private val deleteList = mutableListOf<Int>()

    private lateinit var db: RegisteredAEDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = RegisteredAEDatabase.getInstance(applicationContext)
        _binding = ActivityDeleteDeviceBinding.inflate(layoutInflater)
        _adapter = DeviceAdapter(mutableListOf())

        CoroutineScope(Dispatchers.IO).launch {
            val deviceList = db.registeredAEDAO().getAll()
            adapter.setList(deviceList)
        }

        binding.deviceRecyclerView.adapter = adapter
        binding.deviceRecyclerView.addItemDecoration(ItemPadding(5,5))
        binding.deviceRecyclerView.layoutManager = GridLayoutManager(this, 2, RecyclerView.VERTICAL, false)
        binding.deviceRecyclerView.setHasFixedSize(false)

        binding.toolbar.setTitle("Delete")
        setSupportActionBar(binding.toolbar)
        setContentView(binding.root)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_delete, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            android.R.id.home -> {
                setResult(RESULT_CANCELED)
                finish()
                true
            }
            R.id.menu_ok -> {
                val bundle = Bundle()
                bundle.putIntArray("DELETED_AE", deleteList.toIntArray())
                intent.putExtras(bundle)
                setResult(RESULT_OK, intent)
                finish()
                true
            }
            R.id.menu_cancel -> {
                setResult(RESULT_CANCELED)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

        fun setList(list: List<RegisteredAE>){
            deviceList.clear()
            for(device in list){
                deviceList.add(device)
                notifyItemInserted(deviceList.lastIndex)
            }
        }

        inner class ViewHolder(private val binding: ItemRecyclerDeviceDeleteBinding): RecyclerView.ViewHolder(binding.root) {
            fun bind(device: RegisteredAE){
                binding.deviceName.text = device.applicationName
                binding.deviceStatus.text = if(device.isRegistered) "Registered" else "Unregistered"
                binding.toggleCheckBtn.setOnCheckedChangeListener { _, isChecked ->
                    if(isChecked){
                        deleteList.add(layoutPosition)
                    }else{
                        deleteList.remove(layoutPosition)
                    }
                }
            }
        }
    }
}