package com.k2e7.xsensory.dialogs

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.k2e7.xsensory.databinding.LayoutSearchingToReceiveBinding
import com.k2e7.xsensory.helpers.FilesFetcher
import kotlin.random.Random

class DiscoveringDialog(private val context: Context) {

    private lateinit var dialog: AlertDialog
    private lateinit var b: LayoutSearchingToReceiveBinding
    private val connectionsClient: ConnectionsClient by lazy { Nearby.getConnectionsClient(context) }
    lateinit var payload: Payload
    var connectionEnd = false

    fun show() {
        b = LayoutSearchingToReceiveBinding.inflate(LayoutInflater.from(context))
        b.apply {
            animLottie.visibility = View.VISIBLE
            tvSearching.text = "Searching connection..."
            btnStartTransfer.visibility = View.GONE
        }

        dialog = AlertDialog.Builder(context)
            .setView(b.root)
            .setCancelable(false)
            .show()

        startDiscovery()
    }


    // Callback for connecting to other devices
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
//            if (connectionInfo.endpointName.first().code != clusterId) {
//                connectionsClient.rejectConnection(endpointId)
//                Toast.makeText(context, "$connectionInfo.endpointName : $clusterId", Toast.LENGTH_SHORT).show()
//            } else {
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnFailureListener {
                        dialog.setCancelable(true)
                        b.tvSearching.text = it.localizedMessage ?: "Unknown connection error"
                    }
            }
//        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    // if you were discovery, you can stop
                    connectionsClient.stopDiscovery()
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    // The connection was rejected by one or both sides.
                    b.tvSearching.text = "Connection rejected"
                    dialog.setCancelable(true)
                }

                ConnectionsStatusCodes.STATUS_ERROR -> {
                    // The connection broke before it was able to be accepted.
                    b.tvSearching.text = result.status.statusMessage ?: "Unknown Error"
                    dialog.setCancelable(true)
                }

                else -> {
                    // Unknown status code
                    b.tvSearching.text = "Unknown Error!"
                    dialog.setCancelable(true)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (!connectionEnd) b.tvSearching.text = "Connection disconnected"
        }
    }

    // Callback for payload shared
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // save payload in a variable, so that when transfer
            // succeed we can save payload as file in storage
            if (payload.type == Payload.Type.FILE)
                this@DiscoveringDialog.payload = payload

            Log.e("DiscoveringDialog", "Receiving Complete:${payload.asFile()?.asUri()}")
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // If file shared successfully show Done, and if not then show data transferred
            b.tvSearching.text = if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                dialog.setCancelable(true)
                FilesFetcher.savePayloadAsFile(
                    payload
                )
                connectionEnd = true
                connectionsClient.disconnectFromEndpoint(endpointId)
                "Done\nConnection End"
            } else "Received: ${(update.bytesTransferred) / 1024}KB \nTotal: ${(update.totalBytes) / 1024}KB"
        }
    }

    // Start discovering connection
    private fun startDiscovery() {
        val endpointDiscoveryCallback: EndpointDiscoveryCallback =
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    b.apply {
                        tvSearching.text = info.endpointName
                        animLottie.visibility = View.GONE
                        btnStartTransfer.apply {
                            visibility = View.VISIBLE
                            setOnClickListener {
                                requestConnection(endpointId)
                                this.visibility = View.GONE
                                b.tvSearching.text = "Sending request..."
                            }
                        }
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    b.tvSearching.text = "Connection Lost"
                }

            }

        connectionsClient.startDiscovery(
            context.packageName, endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        )
            .addOnFailureListener {
                b.tvSearching.text = it.localizedMessage ?: "Unknown error occurred"
            }
    }

    private fun requestConnection(endpointId: String) {
        val randomNumber = Random.nextInt()

        connectionsClient.requestConnection(
            "RECEIVER-$randomNumber",
            endpointId,
            connectionLifecycleCallback
        )
            .addOnSuccessListener {
                Log.d("Discovering Dialog", "Connection Requested Successfully")
            }
            .addOnFailureListener {
                b.tvSearching.text = it.localizedMessage ?: "Unknown error occurred"
                Log.d("Discovering Dialog", it.localizedMessage ?: "Unknown")
            }
    }
}