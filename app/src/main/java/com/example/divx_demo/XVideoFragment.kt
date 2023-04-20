package com.example.divx_demo

import androidx.media3.common.MediaItem
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.example.divx_demo.databinding.FragmentXvideoBinding
import java.net.URI

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM_URI = "param_uri"
private const val ARG_PARAM_XID = "param_xid"

private const val TAG = "DIVX-XVIDEOFRAGMENT"

/**
 * A simple [Fragment] subclass.
 * Use the [XVideoFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class XVideoFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var uri: Uri? = null
    private var xid: String? = null

    private val viewModel: DivxViewModel by activityViewModels()
    private var _binding: FragmentXvideoBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            uri = Uri.parse(it.getString(ARG_PARAM_URI))
            xid = it.getString(ARG_PARAM_XID)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        uri?.let { play(it) }
    }


    private fun play(uri: Uri){
        Log.d(TAG, "URI: ${uri.toString()}")
        val mediaItem = MediaItem.Builder().setMediaId(uri.toString()).build()

        with(binding.fragmentXPlayer.player){
            this?.addMediaItem(mediaItem)
            this?.prepare()
            this?.playWhenReady=true
        }
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentXvideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        binding.fragmentXPlayer.player=viewModel.player
        uri?.let{play(it)}
    }



    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment XVideoFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(uri: Uri, xid: String) =
            XVideoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM_URI, uri.toString())
                    putString(ARG_PARAM_XID, xid)
                }
            }

        fun makeArgBundle(xid: String, uri: String): Bundle{
            return Bundle().apply {
                putString(ARG_PARAM_URI, uri.toString())
                putString(ARG_PARAM_XID, xid)
            }
        }
    }
}