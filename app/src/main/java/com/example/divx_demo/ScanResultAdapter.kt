package com.example.divx_demo

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


@SuppressLint("MissingPermission")
class ScanResultAdapter (private val items: List<ScanResult>, private val onClickListener: (device: ScanResult) -> Unit): RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    inner class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {

        val mac_address = view.findViewById<TextView>(R.id.mac_address)
        val signal_strength = view.findViewById<TextView>(R.id.signal_strength)
        val device_name = view.findViewById<TextView>(R.id.device_name)



        fun bind(result: ScanResult) {

            device_name.text = result.device.name ?: "Unnamed"
            mac_address.text = result.device.address
            signal_strength.text = "${result.rssi} dBm"
            view.setOnClickListener {
                onClickListener.invoke(result)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val resultView = inflater.inflate(R.layout.row_scan_result, parent, false)
        return ViewHolder(resultView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = items[position]
        holder.bind(result)
    }

    override fun getItemCount() = items.size
}
