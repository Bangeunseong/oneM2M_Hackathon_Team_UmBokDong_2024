package kr.re.keti.onem2m_hackathon_app.layouts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import kr.re.keti.onem2m_hackathon_app.databinding.ActivityAddFormatBinding
import kr.re.keti.onem2m_hackathon_app.utils.AddListener

class SetServiceAEDialog(private val listener: AddListener) : DialogFragment() {
    private var _binding: ActivityAddFormatBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = ActivityAddFormatBinding.inflate(inflater, container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegister.setOnClickListener {
            listener.setServiceAEName(binding.inputServiceAeName.text.toString())
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }
}