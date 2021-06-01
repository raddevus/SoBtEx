package app.actionmobile.sobtex


import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import app.actionmobile.sobtex.databinding.FragmentFirstBinding
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    var myLabel: TextView? = null
    var myTextbox: EditText? = null
    var mBluetoothAdapter: BluetoothAdapter? = null
    var mmSocket: BluetoothSocket? = null
    var mmDevice: BluetoothDevice? = null
    var mmOutputStream: OutputStream? = null
    var mmInputStream: InputStream? = null
    var workerThread: Thread? = null
    lateinit var readBuffer: ByteArray
    var readBufferPosition = 0
    var counter = 0

    @Volatile
    var stopWorker = false


    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val openButton: Button = view.findViewById(R.id.open) as Button
        val sendButton: Button = view.findViewById(R.id.send) as Button
        val closeButton: Button = view.findViewById(R.id.close) as Button
        myLabel = view.findViewById(R.id.label) as TextView
        myTextbox = view.findViewById(R.id.entry) as EditText

        //Open Button

        //Open Button
        openButton.setOnClickListener(View.OnClickListener {
            try {
                findBT()
                openBT()
            } catch (ex: IOException) {
            }
        })

        //Send Button

        //Send Button
        sendButton.setOnClickListener(View.OnClickListener {
            try {
                sendData()
            } catch (ex: IOException) {
            }
        })

        //Close button

        //Close button
        closeButton.setOnClickListener(View.OnClickListener {
            try {
                closeBT()
            } catch (ex: IOException) {
            }
        })
    }

    fun findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter() as BluetoothAdapter
        if (mBluetoothAdapter == null) {
            myLabel!!.text = "No bluetooth adapter available"
        }
        if (mBluetoothAdapter?.isEnabled() != true) {
            val enableBluetooth = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetooth, 0)
        }
        val pairedDevices = mBluetoothAdapter?.getBondedDevices()
        if (pairedDevices?.size?.compareTo(0)  != 0) {
            for (device in pairedDevices!!) {
                if (device.name == "RADBluex") {
                    mmDevice = device
                    break
                }
            }
        }
        myLabel!!.text = "Bluetooth Device Found"
    }

    @Throws(IOException::class)
    fun openBT() {
        val uuid: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") //Standard SerialPortService ID
        mmSocket = mmDevice!!.createRfcommSocketToServiceRecord(uuid)
        mmSocket?.connect()
        mmOutputStream = mmSocket?.getOutputStream()
        mmInputStream = mmSocket?.getInputStream()
        beginListenForData()
        myLabel!!.text = "Bluetooth Opened"

        // 00001101-0000-1000-8000-00805f9b34fb
        // 00001101-0000-1000-8000-00805F9B34FB
    }

    fun beginListenForData() {
        //val handler : Handler//= Handler()
        val delimiter: Byte = 10 //This is the ASCII code for a newline character
        stopWorker = false
        readBufferPosition = 0
        readBuffer = ByteArray(1024)
        workerThread = Thread {
            while (!Thread.currentThread().isInterrupted && !stopWorker) {
                try {
                    val bytesAvailable: Int? = mmInputStream?.available()
                    if (bytesAvailable!! > 0) {
                        Log.i("FirstFrag", "bytesAvail : " + bytesAvailable.toString())
                        val packetBytes = ByteArray(bytesAvailable)
                        mmInputStream?.read(packetBytes)
                        Log.i("FirstFrag", packetBytes.toString())
                        for (i in 0 until bytesAvailable) {
                            val b = packetBytes[i]
                            Log.i("FirstFrag", "b :" + b.toString())
                            if (b == delimiter) {
                                val encodedBytes = ByteArray(readBufferPosition)
                                System.arraycopy(
                                    readBuffer,
                                    0,
                                    encodedBytes,
                                    0,
                                    encodedBytes.size
                                )
                                val data = encodedBytes.toString(Charsets.US_ASCII)//  String(encodedBytes, "US-ASCII")
                                readBufferPosition = 0
                                Handler(Looper.getMainLooper()).postDelayed({
                                    myLabel!!.text = data
                                }, 10)
                                //handler.post( {  })
                                Log.i("FirstFrag", data)
                            } else {
                                readBuffer[readBufferPosition++] = b
                            }
                        }
                    }
                } catch (ex: IOException) {
                    stopWorker = true
                }
            }
        }
        workerThread!!.start()
    }

    @Throws(IOException::class)
    fun sendData() {
        var msg = myTextbox!!.text.toString()
        // msg += "\n"
        mmOutputStream?.write(msg.toByteArray())
        myLabel!!.text = "Data Sent"
    }

    @Throws(IOException::class)
    fun closeBT() {
        stopWorker = true
        mmOutputStream?.close()
        mmInputStream?.close()
        mmSocket!!.close()
        myLabel!!.text = "Bluetooth Closed"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}